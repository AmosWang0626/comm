package com.amos.im.controller.request;

import com.amos.im.common.BasePacket;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PROJECT: im
 * DESCRIPTION: 退出群聊Request
 *
 * @author amos
 * @date 2019/4/4
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class GroupQuitRequest extends BasePacket {

    private String groupId;

    @Override
    public Byte getCommand() {
        return Command.GROUP_QUIT_REQUEST;
    }
}
