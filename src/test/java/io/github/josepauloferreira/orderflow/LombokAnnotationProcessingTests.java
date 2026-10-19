package io.github.josepauloferreira.orderflow;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

class LombokAnnotationProcessingTests {

  @Test
  void generatesConstructorAndGetter() {
    var sample = new GeneratedSample("working");
    assertThat(sample.getValue()).isEqualTo("working");
  }

  @RequiredArgsConstructor
  private static final class GeneratedSample {

    @Getter private final String value;
  }
}
