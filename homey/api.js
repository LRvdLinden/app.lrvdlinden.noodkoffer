'use strict';

/**
 * Web API of the Noodkoffer app.
 *
 * Protected routes (settings page): getStatus, regenerateCode, setSetting.
 * Public routes (iPhone app on the local network): getInfo, putInventory.
 * putInventory is guarded by the 6-digit pairing code shown on the settings page,
 * with a temporary lock after repeated wrong codes. Public routes always answer
 * with { ok, ... } so the iPhone app does not depend on HTTP error formats.
 */
module.exports = {

  async getStatus({ homey }) {
    let address = null;
    try {
      address = await homey.cloud.getLocalAddress();
    } catch (err) {
      address = null;
    }
    return {
      ...homey.app.getPublicStatus(),
      code: homey.settings.get('pairingCode'),
      notifications: homey.settings.get('notifications') !== false,
      address,
      version: homey.manifest.version,
    };
  },

  async regenerateCode({ homey }) {
    return homey.app.regenerateCode();
  },

  async setSetting({ homey, body }) {
    if (body && typeof body.notifications === 'boolean') {
      homey.settings.set('notifications', body.notifications);
    }
    return { notifications: homey.settings.get('notifications') !== false };
  },

  async getInfo({ homey }) {
    return { ok: true, app: 'noodkoffer', version: homey.manifest.version };
  },

  async putInventory({ homey, body }) {
    try {
      const status = await homey.app.receiveInventory(body);
      return { ok: true, status };
    } catch (err) {
      return { ok: false, error: err.message };
    }
  },

};
