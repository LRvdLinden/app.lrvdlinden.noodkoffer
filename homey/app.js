'use strict';

const Homey = require('homey');
const {
  DAY_MS, todayIn, daysBetween, normaliseInventory, codesMatch, newPairingCode, summarise,
} = require('./lib/kit');

const EVALUATE_INTERVAL = 60 * 60 * 1000; // hourly; thresholds are remembered, so cards fire once
const NOTIFY_THRESHOLDS = [30, 7];
const MAX_FAILED_ATTEMPTS = 10;
const LOCK_MS = 15 * 60 * 1000;
const CHECK_INTERVAL_DAYS = 182;

module.exports = class NoodkofferApp extends Homey.App {

  async onInit() {
    if (!this.homey.settings.get('pairingCode')) {
      this.homey.settings.set('pairingCode', newPairingCode());
    }
    if (this.homey.settings.get('notifications') === null) {
      this.homey.settings.set('notifications', true);
    }
    this.failedAttempts = 0;
    this.lockedUntil = 0;

    this.soonCard = this.homey.flow.getTriggerCard('item_expires_soon');
    this.soonCard.registerRunListener(async (args, state) => state.daysLeft <= args.days
      && (state.prevDaysLeft === null || state.prevDaysLeft > args.days));

    this.expiredCard = this.homey.flow.getTriggerCard('item_expired');

    this.belowCard = this.homey.flow.getTriggerCard('completeness_below');
    this.belowCard.registerRunListener(async (args, state) => state.completeness < args.percent
      && (state.previous === null || state.previous >= args.percent));

    this.checkCard = this.homey.flow.getTriggerCard('check_due');

    this.homey.flow.getConditionCard('kit_complete')
      .registerRunListener(async () => this.getSummary().completeness === 100);
    this.homey.flow.getConditionCard('has_expired_items')
      .registerRunListener(async () => this.getSummary().expired.length > 0);
    this.homey.flow.getActionCard('mark_check_done')
      .registerRunListener(async () => this.markCheckDone());

    this.planToken = await this.homey.flow.createToken('plan', {
      type: 'string', title: this.homey.__('tokens.plan'), value: '',
    });
    this.nextToken = await this.homey.flow.createToken('next', {
      type: 'string', title: this.homey.__('tokens.next'), value: '',
    });

    await this.evaluate();
    this.interval = this.homey.setInterval(() => {
      this.evaluate().catch((err) => this.error('evaluate failed', err.message));
    }, EVALUATE_INTERVAL);
  }

  async onUninit() {
    if (this.interval) this.homey.clearInterval(this.interval);
  }

  today() {
    return todayIn(this.homey.clock.getTimezone());
  }

  getKit() {
    return this.homey.settings.get('kit') || null;
  }

  getSummary() {
    return summarise(this.getKit(), this.today());
  }

  nextCheckDate() {
    const kit = this.getKit();
    const fromPhone = kit && kit.nextCheck;
    const fromHomey = this.homey.settings.get('nextCheckOverride');
    if (fromPhone && fromHomey) return fromPhone > fromHomey ? fromPhone : fromHomey;
    return fromPhone || fromHomey || null;
  }

  /** Public status for the settings page, the widget and the device. */
  getPublicStatus() {
    const summary = this.getSummary();
    return {
      linked: Boolean(this.getKit()),
      lastSync: this.homey.settings.get('lastSync') || null,
      itemCount: summary.itemCount,
      completeness: summary.completeness,
      expiredCount: summary.expired.length,
      next: summary.next ? { name: summary.next.name, date: summary.next.expiresAt, daysLeft: summary.next.daysLeft } : null,
      nextCheck: this.nextCheckDate(),
    };
  }

  // ---- API (called from api.js) -------------------------------------------

  checkCode(code) {
    if (Date.now() < this.lockedUntil) {
      throw Object.assign(new Error('locked'), { statusCode: 429 });
    }
    if (!codesMatch(this.homey.settings.get('pairingCode'), String(code || ''))) {
      this.failedAttempts += 1;
      if (this.failedAttempts >= MAX_FAILED_ATTEMPTS) {
        this.failedAttempts = 0;
        this.lockedUntil = Date.now() + LOCK_MS;
      }
      throw Object.assign(new Error('invalid_code'), { statusCode: 401 });
    }
    this.failedAttempts = 0;
  }

  async receiveInventory(body) {
    this.checkCode(body && body.code);
    const kit = normaliseInventory(body);
    this.homey.settings.set('kit', kit);
    this.homey.settings.set('lastSync', new Date().toISOString());
    await this.evaluate();
    return this.getPublicStatus();
  }

  regenerateCode() {
    const code = newPairingCode();
    this.homey.settings.set('pairingCode', code);
    return { code };
  }

  async markCheckDone() {
    const next = new Date(Date.parse(this.today()) + CHECK_INTERVAL_DAYS * DAY_MS).toISOString().slice(0, 10);
    this.homey.settings.set('nextCheckOverride', next);
    await this.evaluate();
  }

  // ---- Evaluation ----------------------------------------------------------

  async evaluate() {
    const kit = this.getKit();
    const summary = this.getSummary();
    const memory = this.homey.settings.get('memory') || { days: {}, expired: {}, notified: {}, completeness: null, checkFired: null };
    const nextMemory = { days: {}, expired: {}, notified: {}, completeness: summary.completeness, checkFired: memory.checkFired };
    const notify = this.homey.settings.get('notifications') !== false;

    if (kit) {
      for (const item of summary.upcoming) {
        const key = `${item.id}|${item.expiresAt}`;
        const prev = Object.prototype.hasOwnProperty.call(memory.days, key) ? memory.days[key] : null;
        nextMemory.days[key] = item.daysLeft;
        nextMemory.notified[key] = memory.notified[key] || [];
        if (prev !== item.daysLeft) {
          await this.soonCard.trigger(
            { item: item.name, date: item.expiresAt, days_left: item.daysLeft },
            { daysLeft: item.daysLeft, prevDaysLeft: prev },
          ).catch((err) => this.error('soon trigger', err.message));
        }
        for (const threshold of NOTIFY_THRESHOLDS) {
          if (item.daysLeft <= threshold && !nextMemory.notified[key].includes(threshold)) {
            nextMemory.notified[key].push(threshold);
            if (notify) {
              await this.notify('notify.soon', { item: item.name, days: item.daysLeft, date: item.expiresAt });
            }
            break; // one message per evaluation per item
          }
        }
      }

      for (const item of summary.expired) {
        const key = `${item.id}|${item.expiresAt}`;
        nextMemory.expired[key] = true;
        if (!memory.expired[key]) {
          await this.expiredCard.trigger({ item: item.name, date: item.expiresAt })
            .catch((err) => this.error('expired trigger', err.message));
          if (notify) await this.notify('notify.expired', { item: item.name });
        }
      }

      if (memory.completeness !== summary.completeness) {
        await this.belowCard.trigger(
          { completeness: summary.completeness },
          { completeness: summary.completeness, previous: memory.completeness },
        ).catch((err) => this.error('below trigger', err.message));
      }

      const nextCheck = this.nextCheckDate();
      if (nextCheck && daysBetween(this.today(), nextCheck) <= 0 && memory.checkFired !== nextCheck) {
        nextMemory.checkFired = nextCheck;
        await this.checkCard.trigger().catch((err) => this.error('check trigger', err.message));
        if (notify) await this.notify('notify.check', {});
      }
    }

    this.homey.settings.set('memory', nextMemory);

    await this.planToken.setValue((kit && kit.plan) || '');
    await this.nextToken.setValue(summary.next ? `${summary.next.name} (${summary.next.expiresAt})` : '');

    const status = this.getPublicStatus();
    this.homey.emit('kit-updated', status);
    this.homey.api.realtime('kit-updated', status);
  }

  async notify(key, tokens) {
    await this.homey.notifications.createNotification({ excerpt: this.homey.__(key, tokens) })
      .catch((err) => this.error('notification failed', err.message));
  }

};
