"""铁路购票 Java 端到端冒烟：登录 -> 车站 -> 余票查询 -> 下单 -> 异步落库 -> 支付 -> 退票。
用法：python rail_smoke.py   （前置：Java 已起，表已灌 002，G101 已播种）
"""
import json
import time
import urllib.parse
import urllib.request
import uuid
from datetime import date

BASE = "http://127.0.0.1:8080"


def call(method, path, token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(BASE + path, method=method, headers=headers,
                                 data=json.dumps(body).encode() if body is not None else None)
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode())


def login(u, p="123456"):
    d = call("POST", "/api/auth/login", body={"username": u, "password": p})["data"]
    print(f"[登录] {u} -> {d['name']} ({d['role']})")
    return d["token"]


def main():
    token = login("wangzong")

    stations = call("GET", "/api/rail/stations", token=token)["data"]
    by_name = {s["name"]: s["id"] for s in stations}
    fr = by_name["北京南"]
    to = by_name["上海虹桥"]
    print("[车站]", stations)

    today = date.today().isoformat()
    qs = urllib.parse.urlencode({"from": fr, "to": to, "date": today, "seatClass": "二等座"})
    q = call("GET", f"/api/ticket/query?{qs}", token=token)["data"]
    print("[余票查询]", today, "->", [(r["trainCode"], r["seatClass"], r["priceCents"], r["remaining"]) for r in q])
    if not q:
        print("无可用车次，终止"); return
    row = q[0]
    trip_id = row["tripId"]
    before = row["remaining"]

    req_id = "smoke" + uuid.uuid4().hex[:20]
    buy = call("POST", "/api/ticket/buy", token=token,
               body={"tripId": trip_id, "seatClass": "二等座", "fromStationId": fr, "toStationId": to, "requestId": req_id})["data"]
    print("[下单]", buy)

    time.sleep(0.8)
    got = call("GET", f"/api/ticket/orders/request/{req_id}", token=token)["data"]
    print("[异步落库]", got["status"], got["orderNo"])

    pay = call("POST", f"/api/ticket/orders/{req_id}/pay", token=token)["data"]
    print("[支付]", pay["status"])

    my = call("GET", "/api/ticket/orders/my", token=token)["data"]
    print("[我的订单数]", len(my), "最新状态", my[0]["status"])

    cancel = call("POST", f"/api/ticket/orders/{req_id}/cancel", token=token)["data"]
    print("[退票]", cancel["status"])

    after = call("GET", f"/api/ticket/query?{qs}", token=token)["data"][0]["remaining"]
    print(f"[余票回补] before={before} after_cancel={after}")

    print("RAIL_SMOKE_DONE")


if __name__ == "__main__":
    main()
