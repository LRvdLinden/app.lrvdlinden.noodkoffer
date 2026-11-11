'use strict';

const Homey = require('homey');

module.exports = class KitDevice extends Homey.Device {

  async onInit() {
    this.onKitUpdated = (status) => {
      this.applyStatus(status).catch((err) => this.error('update failed', err.message));
    };
    this.homey.on('kit-updated', this.onKitUpdated);
    await this.applyStatus(this.homey.app.getPublicStatus());
  }

  async applyStatus(status) {
    if (!status.linked) {
      await this.setWarning(this.homey.__('device.unavailable'));
    } else {
      await this.unsetWarning();
    }
    await this.setCapabilityValue('kit_completeness', status.linked ? status.completeness : null);
    await this.setCapabilityValue('kit_days_to_expiry', status.next ? status.next.daysLeft : null);
    await this.setCapabilityValue('alarm_generic.expired', status.expiredCount > 0);
    await this.setCapabilityValue('alarm_generic.incomplete', status.linked && status.completeness < 100);
  }

  async onUninit() {
    this.homey.removeListener('kit-updated', this.onKitUpdated);
  }

  async onDeleted() {
    this.homey.removeListener('kit-updated', this.onKitUpdated);
  }

};
