"""Agent 关键纯逻辑单测：工具契约、规则问题识别、结果格式化、模型路由。
用法（venv 内）：python -m pytest tests -q
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "app"))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.agent import orchestrator  # noqa: E402
from app.tools import all_schemas  # noqa: E402


def test_tool_contract_has_expected_five():
    names = {t["function"]["name"] for t in all_schemas()}
    assert names == {"query_tickets", "buy_ticket", "my_orders", "pay_ticket", "cancel_ticket"}


def test_rule_hints_covers_policy_questions():
    # 规则类问题必须命中，避免误走工具调用
    for text in ["退票怎么收费", "儿童票有什么规定", "学生票优惠", "改签规则", "能带多少行李"]:
        assert any(h in text for h in orchestrator.RULE_HINTS), text
    # 购票类问题不命中
    assert not any(h in "帮我查明天北京到上海的二等座" for h in orchestrator.RULE_HINTS)


def test_query_text_format_contains_key_fields():
    rows = [{"tripId": 2, "trainCode": "G101", "from": "北京南", "to": "上海虹桥",
             "departTime": "07:00", "arriveTime": "11:30", "seatClass": "二等座",
             "priceCents": 55300, "remaining": 600}]
    text = orchestrator.AgentService._query_text(rows)
    assert "G101" in text and "55300分" in text and "600张" in text


def test_resolve_model_defaults_to_official_id():
    from app import config
    # 未设置时别名应落到官方带连字符 id；已设置 MODEL_ID_* 也应为 deepseek-v4-pro
    assert config.MODEL_IDS.get("deepseekv4pro") == "deepseek-v4-pro"


def test_buy_trip_must_come_from_query_results():
    rows = [{"tripId": 2, "trainCode": "G101"}]
    assert orchestrator._is_known_trip(rows, 2) is True
    assert orchestrator._is_known_trip(rows, 99) is False
    assert orchestrator._is_known_trip(None, 2) is False
