package com.typeforsheet.backend.domain.record.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.typeforsheet.backend.domain.music.repository.MusicItemRepository;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import com.typeforsheet.backend.domain.record.repository.MusicRecordListRepository;
import com.typeforsheet.backend.domain.user.entity.User;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MusicRecordListServiceTest {
  private final MusicRecordListRepository records = mock(MusicRecordListRepository.class);
  private final MusicItemRepository music = mock(MusicItemRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final MusicRecordListService service = new MusicRecordListService(records, music, users);
  private final LocalDateTime time = LocalDateTime.of(2026, 1, 1, 12, 0, 0, 123456000);

  @BeforeEach
  void setup() {
    when(users.existsById(1L)).thenReturn(true);
    when(music.existsById(2L)).thenReturn(true);
  }

  @Test
  void mineUsesLimitPlusOneAndReturnsTheLastVisibleCursor() {
    MusicRecord first = record(3L, (byte) 9, null, true);
    MusicRecord extra = record(2L, null, "extra", false);
    when(records.findMine(2L, 1L, null, null, 2)).thenReturn(List.of(first, extra));
    when(records.countMine(2L, 1L)).thenReturn(8L);
    var response = service.getMine(1L, 2L, 1, null, null);
    assertEquals(8, response.totalRecordCount());
    assertTrue(response.hasNext());
    assertEquals(3L, response.nextCursor().recordId());
    assertEquals(time, response.nextCursor().createdAt());
    assertEquals(1, response.results().size());
    assertEquals(new BigDecimal("4.5"), response.results().getFirst().rating());
    assertNull(response.results().getFirst().content());
    assertTrue(response.results().getFirst().isPrivate());
  }

  @Test
  void othersMapsAuthorAndNullableFieldsAndLastPage() {
    MusicRecord record = record(4L, null, "text", false);
    User author = mock(User.class);
    when(author.getId()).thenReturn(12L);
    when(author.getNickname()).thenReturn("writer");
    when(record.getUser()).thenReturn(author);
    when(records.findOthers(2L, 1L, time, 8L, 11)).thenReturn(List.of(record));
    when(records.countOthers(2L, 1L)).thenReturn(7L);
    var response = service.getOthers(1L, 2L, 10, time, 8L);
    assertEquals(7, response.totalRecordCount());
    assertFalse(response.hasNext());
    assertNull(response.nextCursor());
    var item = response.results().getFirst();
    assertEquals(12L, item.authorId());
    assertEquals("writer", item.authorNickname());
    assertNull(item.authorProfileImageUrl());
    assertNull(item.rating());
    assertEquals("text", item.content());
  }

  @Test
  void emptyPageKeepsUnpagedTotal() {
    when(records.findMine(2L, 1L, time, 8L, 21)).thenReturn(List.of());
    when(records.countMine(2L, 1L)).thenReturn(5L);
    var response = service.getMine(1L, 2L, 20, time, 8L);
    assertTrue(response.results().isEmpty());
    assertEquals(5, response.totalRecordCount());
    assertFalse(response.hasNext());
    assertNull(response.nextCursor());
  }

  @Test
  void validatesArgumentsAndMissingEntities() {
    assertError(ErrorCode.INVALID_REQUEST, () -> service.getMine(1L, 0L, 1, null, null));
    assertError(ErrorCode.INVALID_REQUEST, () -> service.getMine(1L, 2L, 0, null, null));
    assertError(ErrorCode.INVALID_REQUEST, () -> service.getOthers(1L, 2L, 51, null, null));
    assertError(ErrorCode.INVALID_REQUEST, () -> service.getMine(1L, 2L, 1, time, null));
    assertError(ErrorCode.INVALID_REQUEST, () -> service.getOthers(1L, 2L, 1, null, 4L));
    assertError(ErrorCode.INVALID_REQUEST, () -> service.getMine(1L, 2L, 1, time, 0L));
    assertError(ErrorCode.USER_NOT_FOUND, () -> service.getMine(9L, 2L, 1, null, null));
    assertError(ErrorCode.MUSIC_ITEM_NOT_FOUND, () -> service.getOthers(1L, 9L, 1, null, null));
    verifyNoInteractions(records);
  }

  private MusicRecord record(Long id, Byte rating, String content, boolean isPrivate) {
    MusicRecord record = mock(MusicRecord.class);
    when(record.getId()).thenReturn(id);
    when(record.getRatingHalfSteps()).thenReturn(rating);
    when(record.getContent()).thenReturn(content);
    when(record.isPrivate()).thenReturn(isPrivate);
    when(record.getCreatedAt()).thenReturn(time);
    return record;
  }

  private void assertError(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
    assertEquals(code, assertThrows(BusinessException.class, action).getErrorCode());
  }
}
