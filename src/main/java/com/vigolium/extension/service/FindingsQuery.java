package com.vigolium.extension.service;

public class FindingsQuery {

    private int limit = 50;
    private int offset = 0;
    private String domain;
    private String severity;
    private String moduleName;
    private String moduleType;
    private String findingSource;
    private String scanId;
    private String repoName;
    private String search;
    private String sort = "found_at";
    private String order = "desc";

    public FindingsQuery copy() {
        FindingsQuery copy = new FindingsQuery();
        copy.limit = limit;
        copy.offset = offset;
        copy.domain = domain;
        copy.severity = severity;
        copy.moduleName = moduleName;
        copy.moduleType = moduleType;
        copy.findingSource = findingSource;
        copy.scanId = scanId;
        copy.repoName = repoName;
        copy.search = search;
        copy.sort = sort;
        copy.order = order;
        return copy;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getModuleType() {
        return moduleType;
    }

    public void setModuleType(String moduleType) {
        this.moduleType = moduleType;
    }

    public String getFindingSource() {
        return findingSource;
    }

    public void setFindingSource(String findingSource) {
        this.findingSource = findingSource;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }
}
