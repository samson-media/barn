package com.samsonmedia.barn.cli;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.samsonmedia.barn.jobs.Job;

/**
 * Formats output as XML.
 *
 * <p>Produces machine-readable XML with proper element naming and escaping.
 */
public class XmlFormatter implements OutputFormatter {

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String INDENT = "  ";

    @Override
    public String format(Object value) {
        if (value == null) {
            return XML_HEADER + "\n<null/>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(XML_HEADER).append("\n");

        if (value instanceof Job job) {
            formatJob(sb, job, 0);
        } else if (value instanceof Map<?, ?> map) {
            formatMap(sb, "result", map, 0);
        } else if (value.getClass().isRecord()) {
            formatRecord(sb, value, 0);
        } else {
            sb.append("<result>").append(escapeXml(value.toString())).append("</result>");
        }

        return sb.toString();
    }

    @Override
    public String formatList(List<?> values) {
        StringBuilder sb = new StringBuilder();
        sb.append(XML_HEADER).append("\n");

        if (values == null || values.isEmpty()) {
            sb.append("<results/>");
            return sb.toString();
        }

        sb.append("<results>\n");

        for (Object value : values) {
            if (value instanceof Job job) {
                formatJob(sb, job, 1);
            } else if (value instanceof Map<?, ?> map) {
                formatMap(sb, "item", map, 1);
            } else if (value != null && value.getClass().isRecord()) {
                formatRecord(sb, value, 1);
            } else {
                sb.append(INDENT).append("<item>").append(escapeXml(String.valueOf(value)))
                    .append("</item>\n");
            }
        }

        sb.append("</results>");
        return sb.toString();
    }

    @Override
    public String formatError(String message, Throwable cause) {
        StringBuilder sb = new StringBuilder();
        sb.append(XML_HEADER).append("\n");
        sb.append("<error>\n");
        sb.append(INDENT).append("<message>").append(escapeXml(message)).append("</message>\n");

        if (cause != null) {
            sb.append(INDENT).append("<cause>").append(escapeXml(cause.getMessage()))
                .append("</cause>\n");
            sb.append(INDENT).append("<type>").append(cause.getClass().getSimpleName())
                .append("</type>\n");
        }

        sb.append("</error>");
        return sb.toString();
    }

    private void formatJob(StringBuilder sb, Job job, int depth) {
        String indent = INDENT.repeat(depth);
        String childIndent = INDENT.repeat(depth + 1);

        sb.append(indent).append("<job>\n");
        sb.append(childIndent).append("<id>").append(escapeXml(job.id())).append("</id>\n");
        sb.append(childIndent).append("<state>").append(job.state().toString().toLowerCase())
            .append("</state>\n");

        // Command as nested elements
        sb.append(childIndent).append("<command>\n");
        for (String arg : job.command()) {
            sb.append(childIndent).append(INDENT).append("<arg>").append(escapeXml(arg))
                .append("</arg>\n");
        }
        sb.append(childIndent).append("</command>\n");

        if (job.tag() != null) {
            sb.append(childIndent).append("<tag>").append(escapeXml(job.tag())).append("</tag>\n");
        }

        sb.append(childIndent).append("<created_at>").append(formatTimestamp(job.createdAt()))
            .append("</created_at>\n");

        if (job.startedAt() != null) {
            sb.append(childIndent).append("<started_at>").append(formatTimestamp(job.startedAt()))
                .append("</started_at>\n");
        }

        if (job.finishedAt() != null) {
            sb.append(childIndent).append("<finished_at>").append(formatTimestamp(job.finishedAt()))
                .append("</finished_at>\n");
        }

        if (job.exitCode() != null) {
            sb.append(childIndent).append("<exit_code>").append(job.exitCode())
                .append("</exit_code>\n");
        }

        if (job.error() != null) {
            sb.append(childIndent).append("<error>").append(escapeXml(job.error()))
                .append("</error>\n");
        }

        if (job.pid() != null) {
            sb.append(childIndent).append("<pid>").append(job.pid()).append("</pid>\n");
        }

        if (job.heartbeat() != null) {
            sb.append(childIndent).append("<heartbeat>").append(formatTimestamp(job.heartbeat()))
                .append("</heartbeat>\n");
        }

        if (job.retryCount() > 0) {
            sb.append(childIndent).append("<retry_count>").append(job.retryCount())
                .append("</retry_count>\n");
        }

        if (job.retryAt() != null) {
            sb.append(childIndent).append("<retry_at>").append(formatTimestamp(job.retryAt()))
                .append("</retry_at>\n");
        }

        sb.append(indent).append("</job>\n");
    }

    private void formatMap(StringBuilder sb, String elementName, Map<?, ?> map, int depth) {
        String indent = INDENT.repeat(depth);
        String childIndent = INDENT.repeat(depth + 1);

        sb.append(indent).append("<").append(elementName).append(">\n");

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = toXmlElementName(entry.getKey().toString());
            Object value = entry.getValue();

            if (value == null) {
                sb.append(childIndent).append("<").append(key).append("/>\n");
            } else {
                sb.append(childIndent).append("<").append(key).append(">")
                    .append(escapeXml(value.toString()))
                    .append("</").append(key).append(">\n");
            }
        }

        sb.append(indent).append("</").append(elementName).append(">\n");
    }

    private void formatRecord(StringBuilder sb, Object record, int depth) {
        String indent = INDENT.repeat(depth);
        String childIndent = INDENT.repeat(depth + 1);
        String elementName = toXmlElementName(record.getClass().getSimpleName());

        sb.append(indent).append("<").append(elementName).append(">\n");

        for (RecordComponent component : record.getClass().getRecordComponents()) {
            try {
                Object value = component.getAccessor().invoke(record);
                String name = toXmlElementName(component.getName());

                if (value == null) {
                    sb.append(childIndent).append("<").append(name).append("/>\n");
                } else if (value instanceof List<?> list) {
                    sb.append(childIndent).append("<").append(name).append(">\n");
                    for (Object item : list) {
                        sb.append(childIndent).append(INDENT).append("<item>")
                            .append(escapeXml(item.toString())).append("</item>\n");
                    }
                    sb.append(childIndent).append("</").append(name).append(">\n");
                } else {
                    sb.append(childIndent).append("<").append(name).append(">")
                        .append(escapeXml(formatValue(value)))
                        .append("</").append(name).append(">\n");
                }
            } catch (Exception e) {
                // Skip fields that can't be accessed
            }
        }

        sb.append(indent).append("</").append(elementName).append(">\n");
    }

    private String formatValue(Object value) {
        if (value instanceof Instant instant) {
            return formatTimestamp(instant);
        }
        return value.toString();
    }

    private String formatTimestamp(Instant timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toString();
    }

    private String toXmlElementName(String name) {
        // Convert camelCase to snake_case for XML elements
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
