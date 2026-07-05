package com.itgeo.fitmate.common.enums;

/**
 * @author gzx
 * @description: 列表排序方式
 * @date 2024-05-20 10:00:00
 */
public enum ListSortEnum {

    ASC("asc", "升序排序"),
    DESC("desc", "降序排序");

    public final String code;
    public final String label;

    ListSortEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

}
