package com.amos.im.common.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * PROJECT: im
 * DESCRIPTION: note
 *
 * @author Daoyuan
 * @date 2019/3/21
 */
public class IdUtil {

    private AtomicLong userId = new AtomicLong(0L);
    private AtomicLong groupId = new AtomicLong(0L);

    private static volatile IdUtil idUtil;

    public static IdUtil getInstance() {
        if (idUtil == null) {
            idUtil = new IdUtil();
        }
        return idUtil;
    }

    public Long getUserId() {
        return userId.getAndAdd(1L);
    }

    public String getGroupId() {
        return "g0" + groupId.getAndAdd(1L);
    }

    public String getToken() {
        return "c" + getUserId();
    }

}