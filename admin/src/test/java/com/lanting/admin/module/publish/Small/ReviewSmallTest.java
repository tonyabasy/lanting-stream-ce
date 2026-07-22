package com.lanting.admin.module.publish.Small;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.common.util.SecurityUtils;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.service.FileIndexService;
import com.lanting.admin.module.publish.entity.ReviewEntity;
import com.lanting.admin.module.publish.mapper.ReviewMapper;
import com.lanting.admin.module.publish.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ReviewService Small 测试（纯逻辑，mock FileIndexService + ReviewMapper）。
 */
@DisplayName("ReviewService upsert 与删除逻辑")
class ReviewSmallTest {

    private final ReviewMapper reviewMapper = mock(ReviewMapper.class);
    private final FileIndexService fileIndexService = mock(FileIndexService.class);
    private final ReviewService service = new ReviewService(reviewMapper, fileIndexService);

    private static final long FILE_ID = 1L;
    private static final String HASH_A = "abc123";
    private static final String HASH_B = "def456";

    private FileIndexEntity idx;

    @BeforeEach
    void setUp() {
        idx = new FileIndexEntity();
        idx.setId(FILE_ID);
        idx.setName("test.sql");
        idx.setLatestCommitHash(HASH_A);
        when(fileIndexService.getById(FILE_ID)).thenReturn(idx);
    }

    @Test
    @DisplayName("commitHash 不一致 → 抛 REVIEW_COMMIT_STALE")
    void staleCommitHash_throwsException() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::currentUser).thenReturn("user2");

            assertThrows(BusinessException.class, () ->
                service.review(FILE_ID, HASH_B, "APPROVED", "looks good"));
        }
    }

    @Test
    @DisplayName("同 reviewer 对同一 commit 再评 → update")
    void sameReviewerSameVersion_updatesExisting() {
        ReviewEntity exist = new ReviewEntity();
        exist.setReviewer("user2");
        exist.setResult("REJECTED");

        when(reviewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exist);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::currentUser).thenReturn("user2");
            service.review(FILE_ID, HASH_A, "APPROVED", "looks good");
        }

        verify(reviewMapper).updateById(exist);
        verify(reviewMapper, never()).insert(any(ReviewEntity.class));
        assertEquals("APPROVED", exist.getResult());
        assertEquals("looks good", exist.getComment());
    }

    @Test
    @DisplayName("不同 reviewer 同一 commit 各自独立")
    void differentReviewerSameVersion_createsNew() {
        when(reviewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::currentUser).thenReturn("user3");
            service.review(FILE_ID, HASH_A, "APPROVED", "good");
        }

        verify(reviewMapper).insert(any(ReviewEntity.class));
        verify(reviewMapper, never()).updateById(any(ReviewEntity.class));
    }

    @Test
    @DisplayName("删别人的评审 → 抛 REVIEW_DEL_FORBIDDEN")
    void deleteOtherUserReview_throwsForbidden() {
        ReviewEntity entity = new ReviewEntity();
        entity.setReviewer("user1");

        when(reviewMapper.selectById(1L)).thenReturn(entity);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::currentUser).thenReturn("user2");

            assertThrows(BusinessException.class, () -> service.delete(1L));
        }
    }
}
