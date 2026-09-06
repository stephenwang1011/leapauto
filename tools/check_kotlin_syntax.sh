#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
cat > /tmp/check_mainactivity_kotlin.js <<'EOF'
const fs = require('fs');
const path = process.argv[2];
let code = fs.readFileSync(path, 'utf8');
code = code.replace(/package .*?\n/, '\n');
code = code.replace(/import .*?\n/g, '\n');
code = code.replace(/@AndroidEntryPoint/g, '');
code = code.replace(/class MainActivity/g, 'class MainActivity');
code = code.replace(/@Inject/g, '');
code = code.replace(/lateinit var/g, 'let ');
code = code.replace(/mutableStateOf\(false\)/g, 'false');
code = code.replace(/mutableStateOf\(true\)/g, 'true');
code = code.replace(/mutableStateOf\(0\)/g, '0');
code = code.replace(/mutableStateOf\(0L\)/g, '0');
code = code.replace(/mutableStateOf<[^>]+>\([^)]*\)/g, 'null');
fs.writeFileSync('/tmp/check_mainactivity.js', code);
EOF
node /tmp/check_mainactivity_kotlin.js app/src/main/java/com/leapauto/app/MainActivity.kt
node --check /tmp/check_mainactivity.js
