#!/bin/bash

# 修复 Spider JAR 存储路径脚本
# 用法: ~/yorkspace/github/TV/fix_spider_storage.sh ~/yorkspace/apktool_2.12.1.jar ~/yorkspace/单线路1114/TVBoxOSC/tvbox/spider.jar  ~/yorkspace/单线路1114/TVBoxOSC/tvbox/custom_spider_modified.jar

if [ $# -ne 3 ]; then
    echo "用法: $0 <apktool.jar> <输入jar> <输出jar>"
    exit 1
fi

APKTOOL_JAR="$1"
INPUT_JAR="$2"
OUTPUT_JAR="$3"
OUTPUT_DIR="$(dirname "$OUTPUT_JAR")"
SMALI_DIR="$OUTPUT_DIR/smali_temp$$"

echo "1. 反编译 JAR..."
java -jar "$APKTOOL_JAR" d -f "$INPUT_JAR" -o "$SMALI_DIR" || exit 1

echo "2. 替换硬编码路径为动态调用..."
SMALI_DIR_EXPORT="$SMALI_DIR" python3 << 'PYTHON_SCRIPT'
import os
import re

smali_dir = os.environ['SMALI_DIR_EXPORT']

for root, dirs, files in os.walk(smali_dir):
    for file in files:
        if file.endswith('.smali'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()

            # 替换硬编码路径为 Init.context().getFilesDir()
            # 策略：按方法处理，增加 .locals 并使用新寄存器

            # 先按方法分割内容
            method_pattern = r'(\.method.*?\.end method)'
            methods = re.findall(method_pattern, content, re.DOTALL)

            new_content = content

            for method in methods:
                # 检查方法中是否有需要替换的路径
                path_pattern = r'const-string (v\d+), "(?:/storage/emulated/0|/sdcard|/data/user/0/[^/]+/files)(/[^"]*|)"'
                if not re.search(path_pattern, method):
                    continue

                # 获取当前方法的 .locals 数量
                locals_match = re.search(r'\.locals (\d+)', method)
                if not locals_match:
                    continue

                current_locals = int(locals_match.group(1))
                new_locals = current_locals + 2  # 增加2个寄存器
                temp_reg1 = f'v{current_locals}'
                temp_reg2 = f'v{current_locals + 1}'

                # 更新 .locals
                new_method = method.replace(f'.locals {current_locals}', f'.locals {new_locals}')

                # 替换路径
                def replace_path(match):
                    reg = match.group(1)
                    subpath = match.group(2).lstrip('/') if match.group(2) else ""

                    if subpath:
                        return f'''invoke-static {{}}, Lcom/github/catvod/spider/Init;->context()Landroid/app/Application;

    move-result-object {reg}

    invoke-virtual {{{reg}}}, Landroid/content/Context;->getFilesDir()Ljava/io/File;

    move-result-object {reg}

    invoke-virtual {{{reg}}}, Ljava/io/File;->getAbsolutePath()Ljava/lang/String;

    move-result-object {reg}

    new-instance {temp_reg1}, Ljava/lang/StringBuilder;

    invoke-direct {{{temp_reg1}}}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {{{temp_reg1}, {reg}}}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string {temp_reg2}, "/{subpath}"

    invoke-virtual {{{temp_reg1}, {temp_reg2}}}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {{{temp_reg1}}}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object {reg}'''
                    else:
                        return f'''invoke-static {{}}, Lcom/github/catvod/spider/Init;->context()Landroid/app/Application;

    move-result-object {reg}

    invoke-virtual {{{reg}}}, Landroid/content/Context;->getFilesDir()Ljava/io/File;

    move-result-object {reg}

    invoke-virtual {{{reg}}}, Ljava/io/File;->getAbsolutePath()Ljava/lang/String;

    move-result-object {reg}'''

                new_method = re.sub(path_pattern, replace_path, new_method)
                new_content = new_content.replace(method, new_method)

            if new_content != content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
PYTHON_SCRIPT

echo "3. 替换 Environment.getExternalStorageDirectory() 调用..."
SMALI_DIR_EXPORT="$SMALI_DIR" python3 << 'PYTHON_SCRIPT'
import os
import re

smali_dir = os.environ['SMALI_DIR_EXPORT']

for root, dirs, files in os.walk(smali_dir):
    for file in files:
        if file.endswith('.smali'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()

            # 替换 Environment.getExternalStorageDirectory() 为 Init.context().getFilesDir()
            pattern = r'invoke-static \{\}, Landroid/os/Environment;->getExternalStorageDirectory\(\)Ljava/io/File;\s+move-result-object (v\d+)'

            def replace_env(match):
                reg = match.group(1)
                return f'invoke-static {{}}, Lcom/github/catvod/spider/Init;->context()Landroid/app/Application;\n\n    move-result-object {reg}\n\n    invoke-virtual {{{reg}}}, Landroid/content/Context;->getFilesDir()Ljava/io/File;\n\n    move-result-object {reg}'

            new_content = re.sub(pattern, replace_env, content)

            if new_content != content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
PYTHON_SCRIPT

echo "4. 修复 Context.getFilesDir() 静态调用..."
SMALI_DIR_EXPORT="$SMALI_DIR" python3 << 'PYTHON_SCRIPT'
import os
import re

smali_dir = os.environ['SMALI_DIR_EXPORT']

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
echo "$SMALI_DIR"
rm -rf "$SMALI_DIR"
rm -rf "$OUTPUT_DIR/spider.jar"

echo "完成! 输出: $OUTPUT_JAR"
