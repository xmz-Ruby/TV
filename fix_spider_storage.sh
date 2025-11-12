#!/bin/bash

# 修复 Spider JAR 存储路径脚本
# 用法: ./fix_spider_storage.sh ~/Downloads/apktool_2.12.1.jar spider.jar output.jar

if [ $# -ne 3 ]; then
    echo "用法: $0 <apktool.jar> <输入jar> <输出jar>"
    exit 1
fi

APKTOOL_JAR="$1"
INPUT_JAR="$2"
OUTPUT_JAR="$3"
SMALI_DIR="smali_temp_$$"

echo "1. 反编译 JAR..."
java -jar "$APKTOOL_JAR" d "$INPUT_JAR" -o "$SMALI_DIR" || exit 1

echo "2. 替换 API 调用..."
find "$SMALI_DIR" -name "*.smali" -type f -exec sed -i \
    -e 's|Landroid/os/Environment;->getExternalStorageDirectory()|Landroid/content/Context;->getFilesDir()|g' \
    {} +

echo "3. 替换硬编码路径为动态调用..."
python3 << PYTHON_SCRIPT
import os
import re

smali_dir = "$SMALI_DIR"

for root, dirs, files in os.walk(smali_dir):
    for file in files:
        if file.endswith('.smali'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()

            # 替换硬编码路径为 Init.context().getFilesDir()
            # 匹配 const-string vX, "/storage/emulated/0" 或 "/sdcard" 或 "/data/..."
            pattern = r'const-string (v\d+), "(/storage/emulated/0|/sdcard|/data/user/0/[^"]+/files)"'

            def replace_path(match):
                reg = match.group(1)
                return f'''invoke-static {{}}, Lcom/github/catvod/spider/Init;->context()Landroid/app/Application;

    move-result-object {reg}

    invoke-virtual {{{reg}}}, Landroid/content/Context;->getFilesDir()Ljava/io/File;

    move-result-object {reg}

    invoke-virtual {{{reg}}}, Ljava/io/File;->getAbsolutePath()Ljava/lang/String;

    move-result-object {reg}'''

            new_content = re.sub(pattern, replace_path, content)

            if new_content != content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
PYTHON_SCRIPT

echo "4. 修复 Context.getFilesDir() 静态调用..."
python3 << PYTHON_SCRIPT
import os
import re

smali_dir = "$SMALI_DIR"

for root, dirs, files in os.walk(smali_dir):
    for file in files:
        if file.endswith('.smali'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()

            pattern = r'invoke-static \{\}, Landroid/content/Context;->getFilesDir\(\)Ljava/io/File;\n\n    move-result-object (v\d+)'
            replacement = r'invoke-static {}, Lcom/github/catvod/spider/Init;->context()Landroid/app/Application;\n\n    move-result-object \1\n\n    invoke-virtual {\1}, Landroid/content/Context;->getFilesDir()Ljava/io/File;\n\n    move-result-object \1'

            new_content = re.sub(pattern, replacement, content)

            if new_content != content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
PYTHON_SCRIPT

echo "5. 重新编译..."
java -jar "$APKTOOL_JAR" b "$SMALI_DIR" -o "$OUTPUT_JAR" || exit 1

echo "6. 清理..."
rm -rf "$SMALI_DIR"

echo "完成! 输出: $OUTPUT_JAR"
