package org.fedoraproject.javapackages.validator.config.attribute;

import org.fedoraproject.javadeptools.rpm.RpmInfo;

public interface RequiresConfig {
    boolean allowedRequires(RpmInfo rpm, String value);
}