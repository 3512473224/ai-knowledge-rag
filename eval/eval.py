#!/usr/bin/env python3
"""RAG 黑盒评估：对 golden.jsonl 逐题调用 SSE 问答接口，统计召回率与拒答正确率。

用法：
    python3 eval.py --base http://localhost:8080 --golden golden.jsonl --kb-id 1

跑完后结果写入同目录的 last_result.json，数据看板页会自动展示。
"""
import argparse
import datetime
import json
import os
import time
import urllib.parse
import urllib.request


def stream_chat(base: str, question: str, kb_id: str, timeout: int = 120):
    """调用 POST /api/chat/stream，返回 (answer, sources)。"""
    params = urllib.parse.urlencode({"question": question, "kbId": kb_id})
    req = urllib.request.Request(f"{base}/api/chat/stream?{params}", method="POST")
    answer_parts, sources, event = [], [], ""
    start = time.time()
    first_token_at = None
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        buf = ""
        while True:
            chunk = resp.read(1024)
            if not chunk:
                break
            if first_token_at is None:
                first_token_at = time.time()
            buf += chunk.decode("utf-8", errors="ignore")
            while "\n" in buf:
                line, buf = buf.split("\n", 1)
                if line.startswith("event:"):
                    event = line[6:].strip()
                elif line.startswith("data:"):
                    data = line[5:].strip()
                    if event == "token":
                        answer_parts.append(data)
                    elif event == "sources":
                        try:
                            sources = json.loads(data)
                        except json.JSONDecodeError:
                            pass
                    event = ""
    latency = (first_token_at - start) if first_token_at else (time.time() - start)
    return "".join(answer_parts), sources, latency


def is_refusal(answer: str) -> bool:
    return "无法回答" in answer


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--golden", default="golden.jsonl")
    ap.add_argument("--kb-id", default="1", help="评估针对的知识库 id")
    args = ap.parse_args()

    cases = [json.loads(l) for l in open(args.golden, encoding="utf-8") if l.strip()]
    normal_hit = normal_total = 0
    adv_ok = adv_total = 0
    latencies = []

    for i, c in enumerate(cases, 1):
        q, typ = c["question"], c.get("type", "normal")
        print(f"[{i}/{len(cases)}] {q[:40]}...")
        try:
            answer, sources, latency = stream_chat(args.base, q, args.kb_id)
        except Exception as e:  # noqa: BLE001
            print(f"    ❌ 请求失败: {e}")
            continue
        latencies.append(latency)
        src_files = [s.get("fileName", "") for s in sources]

        if typ == "adversarial":
            adv_total += 1
            ok = is_refusal(answer)
            adv_ok += ok
            print(f"    {'✅' if ok else '⚠️幻觉风险'} 拒答={'是' if ok else '否'}")
        else:
            expected = c.get("expected_files", [])
            normal_total += 1
            hit = (not expected) or any(
                any(exp in f for f in src_files) for exp in expected
            )
            normal_hit += hit
            print(f"    {'✅' if hit else '❌'} 来源命中: {src_files}")

    print("\n===== 评估结果 =====")
    print(f"共 {len(cases)} 题")
    recall = normal_hit / normal_total if normal_total else None
    refusal_acc = adv_ok / adv_total if adv_total else None
    avg_latency = sum(latencies) / len(latencies) if latencies else None
    if normal_total:
        print(f"召回率 Recall@5: {recall:.1%} ({normal_hit}/{normal_total})")
    if adv_total:
        print(f"拒答正确率: {refusal_acc:.1%} ({adv_ok}/{adv_total})")
    if latencies:
        print(f"平均首字延迟: {avg_latency:.1f}s")

    # 落盘：数据看板页读取展示
    result = {
        "time": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "kbId": args.kb_id,
        "total": len(cases),
        "recallAt5": round(recall, 4) if recall is not None else None,
        "recallDetail": f"{normal_hit}/{normal_total}" if normal_total else None,
        "refusalAccuracy": round(refusal_acc, 4) if refusal_acc is not None else None,
        "refusalDetail": f"{adv_ok}/{adv_total}" if adv_total else None,
        "avgFirstTokenLatencySec": round(avg_latency, 2) if avg_latency is not None else None,
    }
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "last_result.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f"\n结果已写入 {out}")


if __name__ == "__main__":
    main()
