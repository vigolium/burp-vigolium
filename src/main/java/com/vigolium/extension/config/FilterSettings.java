package com.vigolium.extension.config;

import com.vigolium.extension.filter.FilterRule;
import java.util.List;

public interface FilterSettings {
    List<FilterRule> getProxyFilterRules();

    void setProxyFilterRules(List<FilterRule> rules);
}
