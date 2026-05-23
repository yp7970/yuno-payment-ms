package com.yuno.notification.mapper;

import com.yuno.notification.model.NotificationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper {
    void insert(NotificationLog log);
}
