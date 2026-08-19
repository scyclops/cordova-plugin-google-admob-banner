```js
document.addEventListener(
  'deviceready',
  function () {
    // 1. Listen for events
    document.addEventListener('admob.ad.load', function () {
      console.log('Ad loaded successfully');
    });

    document.addEventListener('admob.ad.loadfail', function (event) {
      console.error('Ad failed to load:', event.detail);
    });

    document.addEventListener('admob.ad.resize', function (event) {
      console.log('Ad resized to:', event.detail.width, 'x', event.detail.height);
    });

    // 2. Create the bottom adaptive banner
    window.admob.create(
      'ca-app-pub-3940256099942544/6300978111', // Test Ad Unit ID
      function () {
        console.log('Banner ad requested');
      },
      function (error) {
        console.error('Failed to create banner:', error);
      },
    );

    // 3. Destroy banner when needed
    // window.admob.destroy();
  },
  false,
);
```
