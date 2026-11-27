package com.typeforsheet.backend.domain.archive.service;

import com.typeforsheet.backend.domain.archive.dto.ArchiveTimelineResponse;
import com.typeforsheet.backend.domain.archive.dto.ArchiveTimelineResponse.AlbumCover;
import com.typeforsheet.backend.domain.archive.dto.ArchiveTimelineResponse.DaySummary;
import com.typeforsheet.backend.domain.archive.dto.ArchiveTimelineResponse.MonthGroup;
import com.typeforsheet.backend.domain.archive.repository.ArchiveRecordRow;
import com.typeforsheet.backend.domain.archive.repository.ArchiveRepository;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.service.AlbumDetailService;
import com.typeforsheet.backend.domain.music.service.TrackDetailService;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArchiveTimelineService {

  private static final Logger log = LoggerFactory.getLogger(ArchiveTimelineService.class);
  private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
  private static final int MAX_COVERS_PER_DAY = 5;

  private final ArchiveRepository archiveRepository;
  private final UserRepository userRepository;
  private final AlbumDetailService albumDetailService;
  private final TrackDetailService trackDetailService;

  @Transactional(readOnly = true)
  public ArchiveTimelineResponse getTimeline(Long userId, int year) {
    validateYear(year);
    if (!userRepository.existsById(userId)) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }

    TimeRange range = timeRange(year);
    List<ArchiveRecordRow> rows =
        archiveRepository.findTimelineRows(userId, range.startInclusive(), range.endExclusive());
    Map<Long, AlbumMetadata> metadataCache = new LinkedHashMap<>();
    Map<YearMonth, LinkedHashMap<LocalDate, DayAccumulator>> grouped = new LinkedHashMap<>();

    for (ArchiveRecordRow row : rows) {
      LocalDate date = toKoreaDate(row.createdAt());
      DayAccumulator day =
          grouped
              .computeIfAbsent(YearMonth.from(date), ignored -> new LinkedHashMap<>())
              .computeIfAbsent(date, ignored -> new DayAccumulator());
      day.recordCount++;
      AlbumMetadata metadata =
          metadataCache.computeIfAbsent(
              row.musicItemId(),
              ignored -> resolveAlbumMetadata(row.musicItemId(), row.musicType()));
      day.albums.putIfAbsent(metadata.albumKey(), metadata.coverImageUrl());
    }

    List<MonthGroup> months =
        grouped.entrySet().stream()
            .map(
                month ->
                    new MonthGroup(
                        month.getKey().toString(),
                        month.getValue().entrySet().stream()
                            .map(entry -> entry.getValue().toResponse(entry.getKey()))
                            .toList()))
            .toList();
    return new ArchiveTimelineResponse(year, archiveRepository.existsByUser_Id(userId), months);
  }

  private AlbumMetadata resolveAlbumMetadata(Long musicItemId, MusicType musicType) {
    try {
      if (musicType == MusicType.ALBUM) {
        var album = albumDetailService.getAlbum(musicItemId);
        String externalId = album.reference() == null ? null : album.reference().id();
        return new AlbumMetadata(
            externalId == null ? fallbackKey(musicType, musicItemId) : externalId,
            album.coverImageUrl());
      }
      if (musicType == MusicType.TRACK) {
        var track = trackDetailService.getTrack(musicItemId);
        return new AlbumMetadata(
            track.releaseGroupId() == null
                ? fallbackKey(musicType, musicItemId)
                : track.releaseGroupId(),
            track.coverImageUrl());
      }
      throw new IllegalStateException("지원하지 않는 음악 유형입니다: " + musicType);
    } catch (BusinessException exception) {
      log.warn(
          "Archive album metadata unavailable for music {}: {}",
          musicItemId,
          exception.getErrorCode());
      return new AlbumMetadata(fallbackKey(musicType, musicItemId), null);
    }
  }

  private LocalDate toKoreaDate(LocalDateTime createdAt) {
    return createdAt.atZone(ZoneId.systemDefault()).withZoneSameInstant(KOREA_ZONE).toLocalDate();
  }

  private TimeRange timeRange(int year) {
    try {
      ZoneId storageZone = ZoneId.systemDefault();
      LocalDateTime start =
          Year.of(year)
              .atDay(1)
              .atStartOfDay(KOREA_ZONE)
              .withZoneSameInstant(storageZone)
              .toLocalDateTime();
      LocalDateTime end =
          Year.of(year)
              .plusYears(1)
              .atDay(1)
              .atStartOfDay(KOREA_ZONE)
              .withZoneSameInstant(storageZone)
              .toLocalDateTime();
      return new TimeRange(start, end);
    } catch (DateTimeException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
  }

  private void validateYear(int year) {
    if (year < 1 || year >= Year.MAX_VALUE) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
  }

  private String fallbackKey(MusicType musicType, Long musicItemId) {
    return musicType + ":" + musicItemId;
  }

  private record TimeRange(LocalDateTime startInclusive, LocalDateTime endExclusive) {}

  private record AlbumMetadata(String albumKey, String coverImageUrl) {}

  private static final class DayAccumulator {
    private long recordCount;
    private final LinkedHashMap<String, String> albums = new LinkedHashMap<>();

    private DaySummary toResponse(LocalDate date) {
      List<AlbumCover> covers = new ArrayList<>(MAX_COVERS_PER_DAY);
      albums.entrySet().stream()
          .limit(MAX_COVERS_PER_DAY)
          .forEach(entry -> covers.add(new AlbumCover(entry.getKey(), entry.getValue())));
      return new DaySummary(date, recordCount, albums.size(), List.copyOf(covers));
    }
  }
}
