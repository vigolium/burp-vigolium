package com.vigolium.extension.service;

public class HttpRecordsQuery {

    private int limit = 50;
    private int offset = 0;
    private String domain;
    private String method;
    private String path;
    private String statusCode;
    private String contentType;
    private String search;
    private String source;
    private Integer minRisk;
    private String remark;
    private String sort = "created_at";
    private String order = "desc";

    public HttpRecordsQuery copy() {
        HttpRecordsQuery copy = new HttpRecordsQuery();
        copy.limit = limit;
        copy.offset = offset;
        copy.domain = domain;
        copy.method = method;
        copy.path = path;
        copy.statusCode = statusCode;
        copy.contentType = contentType;
        copy.search = search;
        copy.source = source;
        copy.minRisk = minRisk;
        copy.remark = remark;
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

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Integer getMinRisk() {
        return minRisk;
    }

    public void setMinRisk(Integer minRisk) {
        this.minRisk = minRisk;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
