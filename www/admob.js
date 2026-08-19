var exec = require('cordova/exec');

var admob = {
    /**
     * Creates and displays an adaptive banner at the bottom of the screen.
     * @param {string} adUnitId
     * @param {Function} [success]
     * @param {Function} [failure]
     */
    create: function (adUnitId, success, failure) {
        if (!adUnitId || typeof adUnitId !== 'string') {
            if (typeof failure === 'function') {
                failure('adUnitId is required and must be a string');
            }
            return;
        }
        exec(success, failure, 'AdMobBannerPlugin', 'create', [adUnitId]);
    },

    /**
     * Destroys and removes the banner from view.
     * @param {Function} [success]
     * @param {Function} [failure]
     */
    destroy: function (success, failure) {
        exec(success, failure, 'AdMobBannerPlugin', 'destroy', []);
    },

    /**
     * Internal bridge event emitter using standard CustomEvent.
     * @param {string} name
     * @param {*} [detail]
     * @private
     */
    _emitEvent: function (name, detail) {
        var event;
        if (detail !== undefined) {
            event = new CustomEvent(name, { detail: detail });
        } else {
            event = new CustomEvent(name);
        }
        document.dispatchEvent(event);
    },
};

module.exports = admob;
