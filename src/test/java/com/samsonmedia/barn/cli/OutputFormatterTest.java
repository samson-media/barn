package com.samsonmedia.barn.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for OutputFormatter.
 */
class OutputFormatterTest {

    @Nested
    class ForFormat {

        @Test
        void forFormat_withHuman_shouldReturnHumanFormatter() {
            OutputFormatter formatter = OutputFormatter.forFormat(OutputFormat.HUMAN);

            assertThat(formatter).isInstanceOf(HumanFormatter.class);
        }

        @Test
        void forFormat_withJson_shouldReturnJsonFormatter() {
            OutputFormatter formatter = OutputFormatter.forFormat(OutputFormat.JSON);

            assertThat(formatter).isInstanceOf(JsonFormatter.class);
        }

        @Test
        void forFormat_withXml_shouldReturnXmlFormatter() {
            OutputFormatter formatter = OutputFormatter.forFormat(OutputFormat.XML);

            assertThat(formatter).isInstanceOf(XmlFormatter.class);
        }
    }
}
