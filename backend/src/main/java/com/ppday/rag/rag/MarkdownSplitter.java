package com.ppday.rag.rag;

import com.ppday.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* 两级切分策略：
* 1. 先按 Markdown 标题（# / ## / ###）做语义粗切，保证同一主题不被拆散；
* 2. 粗块超过 maxChars 时再按段落做滑动窗口细切，overlap 保留上下文，
* 避免答案恰好落在切分边界上导致检索丢失。
* 每个 chunk 会拼上所属标题作为前缀，增强 embedding 的语义完整性。
*/
@Component
@RequiredArgsConstructor
public class MarkdownSplitter {

private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,3})\\s+(.+)$");

private final RagProperties properties;

public List<String> split(String text) {
List<Section> sections = splitByHeading(text);
List<String> chunks = new ArrayList<>();
int maxChars = properties.getChunk().getMaxChars();
int overlap = properties.getChunk().getOverlapChars();
for (Section section: sections) {
String body = section.body();
if (body.length() <= maxChars) {
chunks.add(withHeading(section.heading(), body));
} else {
chunks.addAll(slidingWindow(section.heading(), body, maxChars, overlap));
}
}
return chunks;
}

private List<Section> splitByHeading(String text) {
List<Section> sections = new ArrayList<>();
Matcher matcher = HEADING.matcher(text);
List<int[]> bounds = new ArrayList<>();
List<String> headings = new ArrayList<>();
while (matcher.find()) {
bounds.add(new int[]{matcher.start(), matcher.end()});
headings.add(matcher.group(2).trim());
}
if (bounds.isEmpty()) {
sections.add(new Section("", text.trim()));
return sections;
}
// 标题之前的前言部分
if (bounds.get(0)[0] > 0) {
String preface = text.substring(0, bounds.get(0)[0]).trim();
if (!preface.isEmpty()) {
sections.add(new Section("", preface));
}
}
for (int i = 0; i < bounds.size(); i++) {
int bodyStart = bounds.get(i)[1];
int bodyEnd = (i + 1 < bounds.size())? bounds.get(i + 1)[0]: text.length();
String body = text.substring(bodyStart, bodyEnd).trim();
if (!body.isEmpty()) {
sections.add(new Section(headings.get(i), body));
}
}
return sections;
}

private List<String> slidingWindow(String heading, String body, int maxChars, int overlap) {
List<String> chunks = new ArrayList<>();
// 按段落切，段落内再按句号兜底，保证不在句子中间硬切
String[] paragraphs = body.split("\\n\\s*\\n");
StringBuilder current = new StringBuilder();
for (String paragraph: paragraphs) {
String p = paragraph.trim();
if (p.isEmpty()) {
continue;
}
if (current.length() + p.length() + 2 > maxChars && current.length() > 0) {
chunks.add(withHeading(heading, current.toString().trim()));
// 滑动窗口：保留上一块的尾部 overlap 个字符
String prev = current.toString();
current = new StringBuilder(prev.substring(Math.max(0, prev.length() - overlap)));
current.append("\n\n");
}
current.append(p).append("\n\n");
}
if (!current.toString().trim().isEmpty()) {
chunks.add(withHeading(heading, current.toString().trim()));
}
return chunks;
}

private String withHeading(String heading, String body) {
return heading.isEmpty()? body: ("\n" + body);
}

private record Section(String heading, String body) {
}
}
