"""Java 端到端冒烟：登录 -> 资源 -> 权限校验(拒/放) -> 抢票(含越权拒绝) -> 幂等。

用法：python e2e_smoke.py   （前置：Java 服务 + Redis/MySQL 已起）
"""
import json
import time
import urllib.error
import urllib.request
import uuid

BASE = "http://127.0.0.1:8080"


def call(method, path, token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(BASE + path, method=method, headers=headers,
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode() or "{}")


def login(username):
    st, body = call("POST", "/api/auth/login", body={"username": username, "password": "123456"})
    assert body["code"] == 0, f"{username} 登录失败：{body}"
    print(f"[登录] {username} -> {body['data']['name']}（{body['data']['department']}）")
    return body["data"]["token"]


def resource_by_type(token, rtype):
    st, body = call("GET", "/api/resources", token=token)
    return next(r for r in body["data"] if r["type"] == rtype)


def perm(token, rtype):
    st, body = call("POST", "/api/perms/check", token=token, body={"resourceType": rtype})
    return body["data"]


def main():
    zhang = login("zhanggong")  # 高级工程师
    wang = login("wangzong")    # 高管
    li = login("lizhu")         # 实习生

    exec_res = resource_by_type(wang, "EXEC_SHUTTLE")
    print(f"[资源] 总裁班车 id={exec_res['id']} 库存={exec_res['totalStock']}")

    # 1) 权限校验：高级工程师/实习生 拒，高管 放
    for name, token in [("zhang", zhang), ("li", li)]:
        d = perm(token, "EXEC_SHUTTLE")
        print(f"[权限] {name} 约总裁班车 -> allowed={d['allowed']}  {d['reason']}")
    d = perm(wang, "EXEC_SHUTTLE")
    print(f"[权限] wang 约总裁班车 -> allowed={d['allowed']}  {d['reason']}")

    # 2) 越权抢票：高级工程师直接调 /seckill 也被 Java 拦（双重保障）
    st, body = call("POST", "/api/seckill", token=zhang,
                    body={"resourceId": exec_res["id"], "requestId": uuid.uuid4().hex[:24]})
    print(f"[越权] zhang 抢总裁班车 -> http={st} code={body['code']} msg={body['msg']}")

    # 3) 高管抢票成功
    req_id = uuid.uuid4().hex[:24]
    st, body = call("POST", "/api/seckill", token=wang,
                    body={"resourceId": exec_res["id"], "requestId": req_id})
    print(f"[抢票] wang -> code={body['code']} data={body['data']}")

    time.sleep(0.5)
    # 4) 异步落库查询
    st, body = call("GET", f"/api/orders/{req_id}", token=wang)
    order = body["data"]
    print(f"[落库] 订单 status={order and order['status']} seat={order and order['seatNo']}")

    # 5) 幂等：同一 request_id 重复提交 -> 返回第一次结果，不再扣库存
    st, body = call("POST", "/api/seckill", token=wang,
                    body={"resourceId": exec_res["id"], "requestId": req_id})
    print(f"[幂等] 同 request_id 重提 -> code={body['code']} data={body['data']}")

    # 6) 高库存资源成功路径（员工班车）
    shuttle = resource_by_type(wang, "SHUTTLE")
    req2 = uuid.uuid4().hex[:24]
    st, body = call("POST", "/api/seckill", token=zhang,
                    body={"resourceId": shuttle["id"], "requestId": req2})
    print(f"[抢票] zhang 员工班车 -> code={body['code']} data={body['data']}")

    print("SMOKE_DONE")


if __name__ == "__main__":
    main()
