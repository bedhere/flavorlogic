/* Immersive public-home motion. No framework, no mutation of the app shell. */
(function () {
  'use strict';

  var root = document.querySelector('.landing-home');
  var experience = document.getElementById('landing-top');
  var canvas = document.getElementById('landing-cookie-canvas');
  var fallback = document.querySelector('.landing-cookie-fallback');
  if (!root || !experience || !canvas) { return; }

  var reducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var stageWidth = 1726;
  var stageHeight = 1195;
  var target = 0;
  var progress = 0;
  var last = performance.now();
  var renderer = null;
  var scene = null;
  var camera = null;
  var cookieGroup = null;
  var frameId = 0;
  var modelReady = false;

  function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
  function lerp(a, b, t) { return a + (b - a) * t; }
  function ease(t) { return t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; }

  function updateTarget() {
    var range = Math.max(1, experience.offsetHeight - window.innerHeight);
    target = clamp((window.scrollY - experience.offsetTop) / range, 0, 1);
  }

  function updateLayout() {
    var width = window.innerWidth;
    var height = window.innerHeight;
    var scale = Math.min(width / stageWidth, height / stageHeight);
    if (width <= 640) { scale = Math.max(width / stageWidth, height / stageHeight); }
    root.style.setProperty('--landing-stage-scale', String(Math.min(1, scale)));
    if (renderer && camera) {
      // Keep the Three.js world vertically stable but match the camera to the
      // real viewport ratio. Rendering a 1726x1195 canvas into a different CSS
      // ratio stretches the cookie, especially on narrow screens.
      var aspect = width / Math.max(1, height);
      camera.top = stageHeight / 2;
      camera.bottom = -stageHeight / 2;
      camera.left = -(stageHeight * aspect) / 2;
      camera.right = (stageHeight * aspect) / 2;
      camera.updateProjectionMatrix();
      renderer.setSize(Math.max(1, width), Math.max(1, height), false);
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.65));
    }
  }

  function setStaticState() {
    root.classList.add('landing-loop-visible');
    root.style.setProperty('--landing-bg-y', '-8vh');
    var hero = root.querySelector('.landing-title-hero');
    var intro = root.querySelector('.landing-intro');
    if (hero) { hero.style.opacity = '1'; }
    if (intro) { intro.style.opacity = '1'; }
    if (cookieGroup) {
      cookieGroup.position.set(window.innerWidth <= 640 ? 0 : 300, window.innerWidth <= 640 ? -360 : -240, 0);
      cookieGroup.rotation.set(0, 0, 0);
      cookieGroup.scale.setScalar(window.innerWidth <= 640 ? .78 : .9);
    }
    if (fallback) { fallback.style.opacity = '1'; }
  }

  function addFallback() {
    if (fallback) { fallback.style.opacity = '1'; }
  }

  function initThree() {
    if (!window.THREE || !window.THREE.GLTFLoader || !canvas) { addFallback(); return; }
    try {
      scene = new THREE.Scene();
      camera = new THREE.OrthographicCamera(-stageWidth / 2, stageWidth / 2, stageHeight / 2, -stageHeight / 2, .1, 4000);
      camera.position.z = 2000;
      renderer = new THREE.WebGLRenderer({ canvas: canvas, alpha: true, antialias: true, powerPreference: 'high-performance' });
      renderer.setClearColor(0x000000, 0);
      renderer.outputColorSpace = THREE.SRGBColorSpace || renderer.outputEncoding;
      scene.add(new THREE.HemisphereLight(0xfff8e8, 0x704019, .72));
      var key = new THREE.DirectionalLight(0xfff4dc, .82);
      key.position.set(-450, 650, 1200);
      scene.add(key);
      var rim = new THREE.DirectionalLight(0xffc98e, .14);
      rim.position.set(600, -100, 500);
      scene.add(rim);
      cookieGroup = new THREE.Group();
      var modelRoot = new THREE.Group();
      cookieGroup.add(modelRoot);
      scene.add(cookieGroup);
      updateLayout();

      var loader = new THREE.GLTFLoader();
      loader.load('assets/cookie.glb', function (gltf) {
        var model = gltf.scene;
        var box = new THREE.Box3().setFromObject(model);
        var size = box.getSize(new THREE.Vector3());
        var center = box.getCenter(new THREE.Vector3());
        model.position.sub(center);
        var diameter = Math.max(size.x, size.y) || 1;
        model.scale.setScalar(600 / diameter);
        var maxAnisotropy = renderer.capabilities.getMaxAnisotropy();
        model.traverse(function (object) {
          if (!object.isMesh) { return; }
          var materials = Array.isArray(object.material) ? object.material : [object.material];
          materials.forEach(function (material) {
            if (!material) { return; }
            // The archived GLB is heavily orange. Desaturate the baked map in
            // the material shader so the cookie stays close to the supplied
            // light golden reference while preserving the chocolate chips.
            if (material.color && material.color.setRGB) { material.color.setRGB(1, 1, 1); }
            if (material.map && !material.userData.landingCookieTone) {
              material.userData.landingCookieTone = true;
              material.onBeforeCompile = function (shader) {
                shader.fragmentShader = shader.fragmentShader.replace(
                  '#include <map_fragment>',
                  '#include <map_fragment>\n' +
                  'float cookieLuma = dot(diffuseColor.rgb, vec3(0.299, 0.587, 0.114));\n' +
                  'diffuseColor.rgb = mix(vec3(cookieLuma), diffuseColor.rgb, 0.48);\n' +
                  'diffuseColor.rgb = pow(max(diffuseColor.rgb, vec3(0.0)), vec3(0.9));\n' +
                  'diffuseColor.rgb *= vec3(1.06, 0.98, 0.82);'
                );
              };
            }
            material.metalness = 0;
            material.roughness = Math.max(.72, material.roughness || 0);
            if (material.map) {
              if (THREE.SRGBColorSpace) { material.map.colorSpace = THREE.SRGBColorSpace; }
              material.map.anisotropy = Math.min(8, maxAnisotropy);
            }
            material.needsUpdate = true;
          });
        });
        modelRoot.add(model);
        modelReady = true;
        if (fallback) { fallback.style.opacity = '0'; }
      }, undefined, function () {
        addFallback();
      });
    } catch (error) {
      addFallback();
    }
  }

  function render(now) {
    var dt = Math.min(64, now - last);
    last = now;
    if (!reducedMotion) {
      progress += (target - progress) * (1 - Math.exp(-dt / 220));
    } else {
      progress = 0;
    }
    var sceneProgress = ease(clamp((progress - .05) / .88, 0, 1));
    root.style.setProperty('--landing-bg-y', (-sceneProgress * Math.min(160, window.innerHeight * .22)) + 'px');
    var hero = root.querySelector('.landing-title-hero');
    var intro = root.querySelector('.landing-intro');
    var loopTitle = root.querySelector('.landing-title-loop');
    if (hero) { hero.style.opacity = String(1 - clamp((progress - .08) / .42, 0, 1)); }
    if (intro) { intro.style.opacity = String(1 - clamp((progress - .06) / .32, 0, 1)); }
    if (loopTitle) {
      var loopOpacity = clamp((progress - .42) / .24, 0, 1);
      loopTitle.style.opacity = String(loopOpacity);
      loopTitle.style.transform = window.innerWidth <= 640
        ? 'translateY(' + ((1 - loopOpacity) * 36) + 'px)'
        : 'translate(-50%, ' + ((1 - loopOpacity) * 36) + 'px)';
    }
    root.classList.toggle('landing-loop-visible', progress > .52 || reducedMotion);
    if (cookieGroup && modelReady) {
      var mobile = window.innerWidth <= 640;
      cookieGroup.position.x = lerp(mobile ? 0 : 300, 0, sceneProgress) + Math.sin(sceneProgress * Math.PI) * -18;
      cookieGroup.position.y = lerp(mobile ? -360 : -240, mobile ? -250 : -120, sceneProgress);
      cookieGroup.position.z = 0;
      cookieGroup.rotation.y = sceneProgress * Math.PI;
      cookieGroup.rotation.z = lerp(-.07, .17, sceneProgress);
      cookieGroup.scale.setScalar(lerp(mobile ? .78 : .9, mobile ? .7 : .84, sceneProgress));
    }
    if (renderer && scene && camera) { renderer.render(scene, camera); }
    frameId = window.requestAnimationFrame(render);
  }

  window.addEventListener('scroll', updateTarget, { passive: true });
  window.addEventListener('resize', function () { updateLayout(); updateTarget(); }, { passive: true });
  updateLayout();
  updateTarget();
  initThree();
  if (reducedMotion) { setStaticState(); }
  frameId = window.requestAnimationFrame(render);
  window.addEventListener('pagehide', function () { window.cancelAnimationFrame(frameId); }, { once: true });
}());
