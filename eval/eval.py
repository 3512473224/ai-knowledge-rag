#!/usr/bin/env python3
"""RAG 黑盒评估：对 golden.jsonl 逐题调用 SSE 问答接口，统计召回率与拒答正确率。

用法：
    python3 eval.py --base http://localhost:8080 --golden golden.jsonl
"""
import argparse
import json
import time
import urllib.parse
import urllib.request


def stream_chat(base: str, question: str, timeout: int = 120):
    """调用 POST /api/chat/stream，返回 (answer, sources)。"""
    params = urllib.parse.urlencode({"question": question})
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
    args = ap.parse_args()

    cases = [json.loads(l) for l in open(args.golden, encoding="utf-8") if l.strip()]
    normal_hit = normal_total = 0
    adv_ok = adv_total = 0
    latencies = []

    for i, c in enumerate(cases, 1):
        q, typ = c["question"], c.get("type", "normal")
        print(f"[{i}/{len(cases)}] {q[:40]}...")
        try:
            answer, sources, latency = stream_chat(args.base, q)
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
    if normal_total:
        print(f"召回率 Recall@5: {normal_hit / normal_total:.1%} ({normal_hit}/{normal_total})")
    if adv_total:
        print(f"拒答正确率: {adv_ok / adv_total:.1%} ({adv_ok}/{adv_total})")
    if latencies:
        print(f"平均首字延迟: {sum(latencies) / len(latencies):.1f}s")


if __name__ == "__main__":
    main()
