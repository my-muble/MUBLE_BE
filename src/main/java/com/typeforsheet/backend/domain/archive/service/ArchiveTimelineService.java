package com.typeforsheet.backend.domain.archive.service;

import com.typeforsheet.backend.domain.archive.dto.ArchiveDayAlbumResponse;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayAlbumResponse.Album;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayAlbumResponse.AlbumRecord;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayAlbumResponse.Track;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayResponse;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayResponse.AlbumSummary;
import com.typeforsheet.backend.domain.archive.dto.ArchiveTimelineResponse;
import com.typeforsheet.backend.domain.archive.dto.ArchiveTimelineResponse.AlbumCover;
import com.typeforsheet.backend.domain.archive.dto.ArchiveTimelineResponse.DaySummary;
import com.typeforsheet.backend.domain.archive.dto.ArchiveTimelineResponse.MonthGroup;
import com.typeforsheet.backend.domain.archive.repository.ArchiveRecordRow;
import com.typeforsheet.backend.domain.archive.repository.ArchiveRepository;
import com.typeforsheet.backend.domain.music.dto.AlbumDetailResponse.AlbumTrack;
import com.typeforsheet.backend.domain.music.dto.TrackDetailResponse;
import com.typeforsheet.backend.domain.music.entity.ExternalIdType;
import com.typeforsheet.backend.domain.music.entity.MusicProvider;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.repository.MusicExternalIdRepository;
import com.typeforsheet.backend.domain.music.service.AlbumDetailService;
import com.typeforsheet.backend.domain.music.service.TrackDetailService;
import com.typeforsheet.backend.domain.search.provider.MusicBrainzCatalogProvider;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
  private final MusicExternalIdRepository externalIds;
  private final MusicBrainzCatalogProvider catalog;

  @Transactional(readOnly = true)
  public ArchiveTimelineResponse getTimeline(Long userId, int year) {
    validateYear(year);
    requireUser(userId);

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

  @Transactional(readOnly = true)
  public ArchiveDayResponse getDay(Long userId, LocalDate date) {
    List<ArchiveRecordRow> rows = dayRows(userId, date);
    Map<Long, AlbumMetadata> metadataCache = new LinkedHashMap<>();
    Map<String, AlbumAccumulator> albums = new LinkedHashMap<>();
    for (ArchiveRecordRow row : rows) {
      AlbumMetadata metadata =
          metadataCache.computeIfAbsent(
              row.musicItemId(),
              ignored -> resolveAlbumMetadata(row.musicItemId(), row.musicType()));
      albums
          .computeIfAbsent(metadata.albumKey(), ignored -> new AlbumAccumulator(metadata))
          .add(row, metadata);
    }

    List<AlbumSummary> summaries =
        albums.values().stream()
            .map(album -> album.toResponse(enrichTrackAlbum(album.metadata)))
            .toList();
    return new ArchiveDayResponse(date, rows.size(), summaries.size(), summaries);
  }

  @Transactional(readOnly = true)
  public ArchiveDayAlbumResponse getDayAlbum(Long userId, LocalDate date, String albumKey) {
    List<AlbumRecord> albumRecords = new ArrayList<>();
    Map<String, TrackCounter> recordedTracks = new LinkedHashMap<>();
    Map<Long, AlbumMetadata> metadataCache = new LinkedHashMap<>();
    AlbumMetadata selected = null;
    long trackCount = 0;

    for (ArchiveRecordRow row : dayRows(userId, date)) {
      AlbumMetadata metadata =
          metadataCache.computeIfAbsent(
              row.musicItemId(),
              ignored -> resolveAlbumMetadata(row.musicItemId(), row.musicType()));
      if (!albumKey.equals(metadata.albumKey())) {
        continue;
      }
      selected = selected == null ? metadata : mergeAlbumMetadata(selected, metadata);
      if (row.musicType() == MusicType.ALBUM) {
        BigDecimal rating =
            row.ratingHalfSteps() == null
                ? null
                : BigDecimal.valueOf(row.ratingHalfSteps() * 5L, 1);
        albumRecords.add(
            new AlbumRecord(
                row.recordId(),
                row.createdAt()
                    .atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(KOREA_ZONE)
                    .toOffsetDateTime(),
                rating,
                row.content()));
      } else {
        trackCount++;
        TrackDetailResponse track = metadata.track();
        String recordingId =
            track == null || track.reference() == null ? null : track.reference().id();
        String trackKey =
            recordingId == null ? fallbackKey(MusicType.TRACK, row.musicItemId()) : recordingId;
        recordedTracks.computeIfAbsent(
                trackKey,
                ignored ->
                    new TrackCounter(
                        row.musicItemId(), recordingId, track == null ? null : track.title()))
            .count++;
      }
    }

    if (selected == null) {
      throw new BusinessException(ErrorCode.ALBUM_NOT_FOUND);
    }
    selected = enrichTrackAlbum(selected);
    List<Track> tracks = new ArrayList<>();
    for (AlbumTrack track : selected.tracks()) {
      TrackCounter records = recordedTracks.remove(track.recordingId());
      tracks.add(
          new Track(
              records == null ? null : records.trackId,
              track.recordingId(),
              track.discNumber(),
              track.trackNumber(),
              track.title(),
              records == null ? 0 : records.count));
    }
    for (TrackCounter records : recordedTracks.values()) {
      tracks.add(
          new Track(
              records.trackId, records.recordingId, null, null, records.title, records.count));
    }
    return new ArchiveDayAlbumResponse(
        date,
        new Album(
            selected.albumKey(),
            selected.albumId(),
            selected.title(),
            selected.artistName(),
            selected.coverImageUrl()),
        albumRecords.size() + trackCount,
        albumRecords.size(),
        trackCount,
        List.copyOf(albumRecords),
        List.copyOf(tracks));
  }

  private List<ArchiveRecordRow> dayRows(Long userId, LocalDate date) {
    requireUser(userId);
    return archiveRepository.findDayRows(
        userId, storageStart(date), storageStart(date.plusDays(1)));
  }

  private void requireUser(Long userId) {
    if (!userRepository.existsById(userId)) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
  }

  private AlbumMetadata enrichTrackAlbum(AlbumMetadata metadata) {
    if (metadata.albumId() != null || metadata.releaseGroupId() == null) {
      return metadata;
    }
    String id = metadata.releaseGroupId();
    Long albumId =
        externalIds
            .findByProviderAndExternalIdTypeAndExternalId(
                MusicProvider.MUSICBRAINZ, ExternalIdType.RELEASE_GROUP, id)
            .map(mapping -> mapping.getMusicItem().getId())
            .orElse(null);
    try {
      var album = catalog.getAlbumDetail(id);
      return new AlbumMetadata(
          metadata.albumKey(),
          id,
          albumId,
          album.title(),
          album.artistName(),
          metadata.coverImageUrl(),
          album.tracks(),
          metadata.track());
    } catch (BusinessException exception) {
      return new AlbumMetadata(
          metadata.albumKey(),
          id,
          albumId,
          metadata.title(),
          null,
          metadata.coverImageUrl(),
          List.of(),
          metadata.track());
    }
  }

  private AlbumMetadata resolveAlbumMetadata(Long musicItemId, MusicType musicType) {
    try {
      if (musicType == MusicType.ALBUM) {
        var album = albumDetailService.getAlbum(musicItemId);
        String externalId = album.reference() == null ? null : album.reference().id();
        return new AlbumMetadata(
            externalId == null ? fallbackKey(musicType, musicItemId) : externalId,
            externalId,
            musicItemId,
            album.title(),
            album.artistName(),
            album.coverImageUrl(),
            album.tracks(),
            null);
      }
      if (musicType == MusicType.TRACK) {
        var track = trackDetailService.getTrack(musicItemId);
        return new AlbumMetadata(
            track.releaseGroupId() == null
                ? fallbackKey(musicType, musicItemId)
                : track.releaseGroupId(),
            track.releaseGroupId(),
            null,
            track.albumTitle(),
            null,
            track.coverImageUrl(),
            List.of(),
            track);
      }
      throw new IllegalStateException("지원하지 않는 음악 유형입니다: " + musicType);
    } catch (BusinessException exception) {
      log.warn(
          "Archive album metadata unavailable for music {}: {}",
          musicItemId,
          exception.getErrorCode());
      return new AlbumMetadata(
          fallbackKey(musicType, musicItemId),
          null,
          musicType == MusicType.ALBUM ? musicItemId : null,
          null,
          null,
          null,
          List.of(),
          null);
    }
  }

  private LocalDate toKoreaDate(LocalDateTime createdAt) {
    return createdAt.atZone(ZoneId.systemDefault()).withZoneSameInstant(KOREA_ZONE).toLocalDate();
  }

  private TimeRange timeRange(int year) {
    try {
      LocalDate firstDay = Year.of(year).atDay(1);
      return new TimeRange(storageStart(firstDay), storageStart(firstDay.plusYears(1)));
    } catch (DateTimeException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
    }
  }

  private LocalDateTime storageStart(LocalDate date) {
    return date.atStartOfDay(KOREA_ZONE)
        .withZoneSameInstant(ZoneId.systemDefault())
        .toLocalDateTime();
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

  private record AlbumMetadata(
      String albumKey,
      String releaseGroupId,
      Long albumId,
      String title,
      String artistName,
      String coverImageUrl,
      List<AlbumTrack> tracks,
      TrackDetailResponse track) {}

  private static AlbumMetadata mergeAlbumMetadata(AlbumMetadata current, AlbumMetadata candidate) {
    AlbumMetadata details =
        current.albumId() == null && candidate.albumId() != null ? candidate : current;
    String cover =
        current.coverImageUrl() == null ? candidate.coverImageUrl() : current.coverImageUrl();
    return new AlbumMetadata(
        details.albumKey(),
        details.releaseGroupId(),
        details.albumId(),
        details.title(),
        details.artistName(),
        cover,
        details.tracks(),
        details.track());
  }

  private static final class TrackCounter {
    private final Long trackId;
    private final String recordingId;
    private final String title;
    private long count;

    private TrackCounter(Long trackId, String recordingId, String title) {
      this.trackId = trackId;
      this.recordingId = recordingId;
      this.title = title;
    }
  }

  private static final class AlbumAccumulator {
    private AlbumMetadata metadata;
    private long recordCount;
    private long ratedCount;
    private long ratingHalfStepsSum;
    private String contentPreview;

    private AlbumAccumulator(AlbumMetadata metadata) {
      this.metadata = metadata;
    }

    private void add(ArchiveRecordRow row, AlbumMetadata candidate) {
      recordCount++;
      if (row.ratingHalfSteps() != null) {
        ratedCount++;
        ratingHalfStepsSum += row.ratingHalfSteps();
      }
      if (contentPreview == null && row.content() != null && !row.content().isBlank()) {
        contentPreview = row.content();
      }
      metadata = mergeAlbumMetadata(metadata, candidate);
    }

    private AlbumSummary toResponse(AlbumMetadata details) {
      BigDecimal averageRating =
          ratedCount == 0
              ? null
              : BigDecimal.valueOf(ratingHalfStepsSum)
                  .divide(BigDecimal.valueOf(2 * ratedCount), 1, RoundingMode.HALF_UP);
      return new AlbumSummary(
          details.albumKey(),
          details.albumId(),
          details.title(),
          details.artistName(),
          details.coverImageUrl(),
          recordCount,
          averageRating,
          contentPreview);
    }
  }

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
