(function () {
    // Patch only the known background node. No scene exposure, lighting or car materials.
    window.leapSetNightBackground = function (gain, lift) {
        var v = window.viewer;
        var sky = v && v.frameState && v.frameState.skyBox;
        if (!sky || !sky.material) return false;
        var materials = Array.isArray(sky.material) ? sky.material : [sky.material];
        materials.forEach(function (material) {
            if (!material.__leapBackground) {
                if (typeof material.fragmentShader !== 'string' || !material.uniforms) return;
                var main = /void\s+main\s*\(\s*(?:void)?\s*\)/;
                if (!main.test(material.fragmentShader) || material.fragmentShader.indexOf('gl_FragColor') < 0) return;
                material.fragmentShader = material.fragmentShader.replace(main,
                    'uniform float leapBackgroundGain;\nuniform float leapBackgroundLift;\nvoid leapBackgroundMain()') +
                    '\nvoid main() { leapBackgroundMain(); gl_FragColor.rgb = clamp(gl_FragColor.rgb * leapBackgroundGain + vec3(leapBackgroundLift), 0.0, 1.0); }';
                material.uniforms.leapBackgroundGain = { value: 1 };
                material.uniforms.leapBackgroundLift = { value: 0 };
                material.__leapBackground = true;
                material.needsUpdate = true;
            }
            material.uniforms.leapBackgroundGain.value = gain;
            material.uniforms.leapBackgroundLift.value = lift;
        });
        // One short redraw window; repeated slider changes only update uniforms.
        if (typeof v.setFPS === 'function') v.setFPS(30, 80, 'night_background');
        return materials.every(function (m) { return !!m.__leapBackground; });
    };
})();
