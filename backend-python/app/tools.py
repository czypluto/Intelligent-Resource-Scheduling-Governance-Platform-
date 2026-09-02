"""Agent 可见的工具定义。schema 只描述 Java 侧能力，与具体模型无关。

执行路径只有一条：Python 收到工具调用 -> REST 调 Java。此文件里的工具
与 backend-java ToolCatalog 对齐（Java 启动时已做一致性校验）。
"""


def book_resource_schema() -> dict:
    return {
        "type": "function",
        "function": {
            "name": "book_resource",
            "description": "为当前用户预约/抢购某个资源。需要资源 id，返回座位号与结果。",
            "parameters": {
                "type": "object",
                "properties": {
                    "resourceId": {"type": "integer", "description": "资源 id，来自 list_resources"}
                },
                "required": ["resourceId"],
            },
        },
    }


def check_permission_schema() -> dict:
    return {
        "type": "function",
        "function": {
            "name": "check_permission",
            "description": "查询当前用户对某资源类型是否有预约权限，返回允许与否及原因。",
            "parameters": {
                "type": "object",
                "properties": {
                    "resourceType": {"type": "string", "description": "资源类型编码，如 EXEC_SHUTTLE"}
                },
                "required": ["resourceType"],
            },
        },
    }


def all_schemas() -> list[dict]:
    return [book_resource_schema(), check_permission_schema()]
