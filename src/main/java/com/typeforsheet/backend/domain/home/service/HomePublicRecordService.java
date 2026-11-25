package com.typeforsheet.backend.domain.home.service;

import com.typeforsheet.backend.domain.home.dto.response.HomePublicRecordResponse;
import com.typeforsheet.backend.domain.home.repository.HomePublicRecordRepository;
import com.typeforsheet.backend.domain.music.entity.MusicItem;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.service.AlbumDetailService;
import com.typeforsheet.backend.domain.music.service.TrackDetailService;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomePublicRecordService {

  private static final Logger log = LoggerFactory.getLogger(HomePublicRecordService.class);

  private static final int MAX_RECORDS = 10;

  private final HomePublicRecordRepository publicRecords;
  private final AlbumDetailService albums;
  private final TrackDetailService tracks;

  @Transactional(readOnly = true)
  public List<HomePublicRecordResponse> getPublicRecords(Long currentUserId) {

    List<MusicRecord> records =
        publicRecords.findLatestPublicRecordsExcludingUser(
            currentUserId, PageRequest.of(0, MAX_RECORDS));

    Map<Long, HomePublicRecordResponse.Music> musicCache = new HashMap<>();

    List<HomePublicRecordResponse> responses = new ArrayList<>(records.size());

    for (MusicRecord record : records) {

      MusicItem musicItem = record.getMusicItem();

      HomePublicRecordResponse.Music music =
          musicCache.computeIfAbsent(musicItem.getId(), id -> getMusic(musicItem));

      Byte ratingHalfSteps = record.getRatingHalfSteps();

      BigDecimal rating =
          ratingHalfSteps == null
              ? null
              : BigDecimal.valueOf(ratingHalfSteps.longValue())
                  .divide(BigDecimal.valueOf(2), 1, RoundingMode.UNNECESSARY);

      String contentPreview = record.getContent();

      if (contentPreview != null && contentPreview.isBlank()) {
        contentPreview = null;
      }

      responses.add(
          new HomePublicRecordResponse(
              record.getId(),
              record.getUser().getNickname(),
              music,
              rating,
              contentPreview,
              record.getCreatedAt()));
    }

    return List.copyOf(responses);
  }

  private HomePublicRecordResponse.Music getMusic(MusicItem musicItem) {

    Long musicId = musicItem.getId();
    MusicType type = musicItem.getType();

    try {

      if (type == MusicType.ALBUM) {

        var album = albums.getAlbum(musicId);

        return new HomePublicRecordResponse.Music(
            musicId, type, album.title(), album.artistName(), album.coverImageUrl());
      }

      if (type == MusicType.TRACK) {

        var track = tracks.getTrack(musicId);

        return new HomePublicRecordResponse.Music(
            musicId, type, track.title(), track.artistName(), track.coverImageUrl());
      }

      throw new IllegalStateException("지원하지 않는 음악 유형입니다: " + type);

    } catch (BusinessException exception) {

      if (exception.getErrorCode() != ErrorCode.MUSIC_CATALOG_UNAVAILABLE
          && exception.getErrorCode() != ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND) {

        throw exception;
      }

      log.warn(
          "Music metadata unavailable for home music {}: {}", musicId, exception.getErrorCode());

      return new HomePublicRecordResponse.Music(musicId, type, null, null, null);
    }
  }
}
