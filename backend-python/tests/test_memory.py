"""记忆模块单测：超窗触发压缩、近期原文保留、可引用。"""
from app.agent import memory as mem


def test_compress_window_and_keep_recent():
    mem._redis = None  # 强制进程内存路径
    mem._fallback.clear()
    orig = mem._compress

    def fake(summary_old, evicted):
        return "摘要占位"

    mem._compress = fake
    try:
        for i in range(9):  # 9 对 > RAW_LIMIT(10 条=5 对) 会触发多次压缩
            mem.append(123, f"问题{i}", f"回答{i}")
        st = mem.load_state(123)
        assert len(st["msgs"]) <= mem.RAW_LIMIT, len(st["msgs"])
        assert st["summary"] == "摘要占位"
        # 近期原文仍保留最新几条，保证指代可用
        assert st["msgs"][-1]["content"] == "回答8"
    finally:
        mem._compress = orig
        mem.clear(123)
