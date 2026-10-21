package org.example.finflowbackend.system.file;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.system.SystemErrorCode;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 文件业务类型（S38）。objectKey 以其小写为一级目录，值集合固定（V34 注释同步维护）。
 */
public enum FileBizType {

    /** 商户资质证件。 */
    MERCHANT_CERT,
    /** 渠道对账账单。 */
    RECONCILIATION,
    /** 报表导出文件。 */
    EXPORT,
    /** 结算单 PDF/Excel。 */
    SETTLEMENT;

    /** 按 name 解析，非法值抛业务异常（上传入口校验）。 */
    public static FileBizType parse(String name) {
        try {
            return FileBizType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(SystemErrorCode.FILE_TYPE_INVALID, name);
        }
    }

    /** 全部合法值（接口文档/校验提示用）。 */
    public static Set<String> names() {
        Set<String> set = new LinkedHashSet<>();
        for (FileBizType type : values()) {
            set.add(type.name());
        }
        return set;
    }
}
