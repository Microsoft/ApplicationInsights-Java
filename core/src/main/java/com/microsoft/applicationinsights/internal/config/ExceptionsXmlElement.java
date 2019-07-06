package com.microsoft.applicationinsights.internal.config;

import javax.xml.bind.annotation.XmlElement;

public class ExceptionsXmlElement {

    private Integer maxStackSize;
    private Integer maxExceptionTraceLength;

    public Integer getMaxStackSize() {
        return maxStackSize;
    }

    @XmlElement(name="MaxStackSize")
    public void setMaxStackSize(Integer maxStackSize) {
        this.maxStackSize = maxStackSize;
    }

    public Integer getMaxExceptionTraceLength() {
        return maxExceptionTraceLength;
    }

    @XmlElement(name="MaxExceptionTraceLength")
    public void setMaxExceptionTraceLength(Integer maxExceptionTraceLength) {
        this.maxExceptionTraceLength = maxExceptionTraceLength;
    }
}
