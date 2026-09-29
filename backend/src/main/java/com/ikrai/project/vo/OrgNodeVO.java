package com.ikrai.project.vo;

import lombok.Data;

@Data
public class OrgNodeVO {

    private Long id;
    private String name;
    private Long parentId;
    private String parentName;
    private Boolean enabled;
    private Boolean referenced;
    private Boolean hasChildren;
}
