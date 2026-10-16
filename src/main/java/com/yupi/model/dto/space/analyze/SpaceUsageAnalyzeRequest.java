package com.yupi.model.dto.space.analyze;

import lombok.Data;
import lombok.EqualsAndHashCode;

// 空间资源分析请求类，因为已经继承了，所以不用写多余数据了

@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceUsageAnalyzeRequest extends SpaceAnalyzeRequest {

}

