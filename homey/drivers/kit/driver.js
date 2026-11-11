'use strict';

const Homey = require('homey');

module.exports = class KitDriver extends Homey.Driver {

  async onPairListDevices() {
    if (this.getDevices().length > 0) {
      throw new Error(this.homey.__('pair.only_one'));
    }
    return [{
      name: 'Noodkoffer',
      data: { id: 'noodkoffer-kit' },
    }];
  }

};
