// src/main/java/com/moniq/api/ocr/OcrProvider.java
package com.moniq.api.ocr;

import java.io.InputStream;

public interface OcrProvider {

    OcrProviderResult read(InputStream content, String contentType);

    String providerName();
}