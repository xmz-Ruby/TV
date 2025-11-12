#!/bin/bash

# 修复 Spider JAR 存储路径脚本
# 用法: ./fix_spider_storage.sh ~/Downloads/apktool_2.12.1.jar spider.jar output.jar

if [ $# -ne 2 ]; then
    echo "用法: $0 <输入jar> <输出jar>"
    exit 1
fi

APKTOOL_JAR="$1"
INPUT_JAR="$2"
OUTPUT_JAR="$3"
SMALI_DIR="smali_temp_$$"

echo "1. 反编译 JAR..."
java -jar "$APKTOOL_JAR" d "$INPUT_JAR" -o "$SMALI_DIR" || exit 1

echo "2. 替换路径..."
find "$SMALI_DIR" -name "*.smali" -type f -exec sed -i \
    -e 's|Landroid/os/Environment;->getExternalStorageDirectory()|Landroid/content/Context;->getFilesDir()|g' \
    -e 's|/storage/emulated/0|/data/user/0/com.github.tvbox.osc/files|g' \
    -e 's|/sdcard|/data/user/0/com.github.tvbox.osc/files|g' \
    {} +

echo "3. 修复 Context.getFilesDir() 静态调用..."
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

echo "4. 重新编译..."
java -jar "$APKTOOL_JAR" b "$SMALI_DIR" -o "$OUTPUT_JAR" || exit 1

echo "5. 清理..."
rm -rf "$SMALI_DIR"

echo "完成! 输出: $OUTPUT_JAR"
