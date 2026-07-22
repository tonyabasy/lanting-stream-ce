package com.lanting.admin.module.publish.Small;

import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.module.file.service.FileIndexService;
import com.lanting.admin.module.file.service.GitFileService;
import com.lanting.admin.module.file.vo.UncommitVO;
import com.lanting.admin.module.publish.mapper.PublishFileMapper;
import com.lanting.admin.module.publish.mapper.PublishMapper;
import com.lanting.admin.module.publish.service.PublishService;
import com.lanting.admin.module.publish.service.ReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * PublishService Small 测试（纯逻辑，mock 所有协作者）。
 * 仅测方法入口校验——这些路径不依赖 SQLite/JGit。
 */
@DisplayName("PublishService 入参校验")
class PublishSmallTest {

    private final GitFileService gitFileService = mock(GitFileService.class);

    private final PublishService service = new PublishService(
            mock(PublishMapper.class),
            mock(PublishFileMapper.class),
            mock(ReviewService.class),
            mock(FileIndexService.class),
            gitFileService);

    @Test
    @DisplayName("无变更提交 → 抛 NOTHING_TO_COMMIT")
    void commitWithoutChanges_throwsException() {
        UncommitVO empty = new UncommitVO();
        when(gitFileService.uncommit(List.of(1L))).thenReturn(empty);

        assertThrows(BusinessException.class, () ->
                service.addCommittedList(List.of(1L), "msg"));
    }

    @Test
    @DisplayName("空 fileIds 发布 → 抛 EMPTY_PUBLISH")
    void publishEmptyFileIds_throwsException() {
        assertThrows(BusinessException.class, () ->
                service.publish(Collections.emptyList(), "Pub1"));
    }
}
