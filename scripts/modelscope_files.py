"""列出 ModelScope 上某模型的仓库文件清单（用于确认要下的大文件）。
用法：python3 modelscope_files.py <model_id>  （可传 USER/KEY 环境跳过）
"""
import json
import sys
import urllib.request

MODEL = sys.argv[1] if len(sys.argv) > 1 else "Tencent-Hunyuan/WeMM-Embedding-2B"
url = f"https://modelscope.cn/api/v1/models/{MODEL}/repo/files?Revision=master&Recursive=true"
with urllib.request.urlopen(url, timeout=30) as r:
    payload = json.load(r)
data = payload.get("Data") or {}
files = data.get("Files") or []
total = 0
for f in files:
    if f.get("Type") == "blob":
        size = f.get("Size") or 0
        total += size
        print(f"{size:>14,}  {f.get('Name')}")
print("-" * 40)
print(f"总大小约 {total / 1024 / 1024:.1f} MB")
