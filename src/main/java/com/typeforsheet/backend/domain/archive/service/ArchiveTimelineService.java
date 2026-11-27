package com.typeforsheet.backend.domain.archive.service;

import com.typeforsheet.backend.domain.archive.dto.ArchiveDayAlbumResponse;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayAlbumResponse.Album;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayAlbumResponse.AlbumRecord;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayAlbumResponse.Track;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayResponse;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayResponse.AlbumSummary;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayTrackResponse;
import com.typeforsheet.backend.domain.archive.dto.ArchiveDayTrackResponse.TrackRecord;
import com.typeforsheet.backend.domain.archive.dto.ArchiveTimelineResponse;
import com.typeforsheet.backend.domain.archive.dto.ArchiveTimelineResponse.AlbumCover;
import com.typeforsheet.backend.domain.archive.dto.ArchiveTimelineResponse.DaySummary;
import com.typeforsheet.backend.domain.archive.dto.ArchiveTimelineResponse.MonthGroup;
import com.typeforsheet.backend.domain.archive.repository.ArchiveRecordRow;
import com.typeforsheet.backend.domain.archive.repository.ArchiveRepository;
import com.typeforsheet.backend.domain.archive.repository.ArchiveTrackAlbumRepository;
import com.typeforsheet.backend.domain.music.dto.AlbumDetailResponse.AlbumTrack;
import com.typeforsheet.backend.domain.music.dto.TrackDetailResponse;
import com.typeforsheet.backend.domain.music.entity.ExternalIdType;
import com.typeforsheet.backend.domain.music.entity.MusicExternalId;
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
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private final ArchiveTrackAlbumRepository trackAlbums;

  @Transactional
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

  @Transactional
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

  @Transactional
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
        albumRecords.add(
            new AlbumRecord(
                row.recordId(),
                koreaTime(row.createdAt()),
                rating(row.ratingHalfSteps()),
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

  @Transactional
  public ArchiveDayTrackResponse getDayTrack(
      Long userId, LocalDate date, String albumKey, Long trackId) {
    requireUser(userId);
    List<ArchiveRecordRow> rows =
        archiveRepository.findDayMusicRows(
            userId, trackId, MusicType.TRACK, storageStart(date), storageStart(date.plusDays(1)));
    if (rows.isEmpty()) {
      throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
    }

    AlbumMetadata metadata = resolveAlbumMetadata(trackId, MusicType.TRACK);
    if (!albumKey.equals(metadata.albumKey())) {
      throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
    }

    TrackDetailResponse track = metadata.track();
    String recordingId = track == null || track.reference() == null ? null : track.reference().id();
    AlbumTrack albumTrack =
        recordingId == null
            ? null
            : enrichTrackAlbum(metadata).tracks().stream()
                .filter(candidate -> recordingId.equals(candidate.recordingId()))
                .findFirst()
                .orElse(null);
    var trackInfo =
        new ArchiveDayTrackResponse.Track(
            trackId,
            albumTrack == null ? null : albumTrack.discNumber(),
            albumTrack == null ? null : albumTrack.trackNumber(),
            albumTrack != null && albumTrack.title() != null
                ? albumTrack.title()
                : track == null ? null : track.title(),
            track == null ? null : track.artistName(),
            metadata.coverImageUrl());
    List<TrackRecord> records =
        rows.stream()
            .map(
                row ->
                    new TrackRecord(
                        row.recordId(),
                        koreaTime(row.createdAt()),
                        rating(row.ratingHalfSteps()),
                        row.content()))
            .toList();
    return new ArchiveDayTrackResponse(date, albumKey, trackInfo, records.size(), records);
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
    if (musicType == MusicType.ALBUM) {
      String releaseGroupId =
          externalIds
              .findFirstByMusicItem_IdAndProviderAndExternalIdTypeOrderByIdAsc(
                  musicItemId, MusicProvider.MUSICBRAINZ, ExternalIdType.RELEASE_GROUP)
              .map(MusicExternalId::getExternalId)
              .orElse(null);
      String key = releaseGroupId == null ? fallbackKey(musicType, musicItemId) : releaseGroupId;
      try {
        var album = albumDetailService.getAlbum(musicItemId);
        return new AlbumMetadata(
            key,
            releaseGroupId,
            musicItemId,
            album.title(),
            album.artistName(),
            album.coverImageUrl(),
            album.tracks(),
            null);
      } catch (BusinessException exception) {
        logMetadataFailure(musicItemId, exception);
        return new AlbumMetadata(
            key, releaseGroupId, musicItemId, null, null, null, List.of(), null);
      }
    }
    if (musicType != MusicType.TRACK) {
      throw new IllegalStateException("지원하지 않는 음악 유형입니다: " + musicType);
    }

    var remembered = trackAlbums.find(musicItemId);
    TrackDetailResponse track = null;
    try {
      track = trackDetailService.getTrack(musicItemId);
    } catch (BusinessException exception) {
      if (remembered.isEmpty()) {
        throw exception;
      }
      logMetadataFailure(musicItemId, exception);
    }
    String releaseGroupId =
        remembered.isPresent()
            ? remembered.get().releaseGroupId()
            : trackAlbums.remember(musicItemId, track.releaseGroupId()).releaseGroupId();
    boolean matchingAlbum = track != null && Objects.equals(releaseGroupId, track.releaseGroupId());
    return new AlbumMetadata(
        releaseGroupId == null ? fallbackKey(musicType, musicItemId) : releaseGroupId,
        releaseGroupId,
        null,
        matchingAlbum ? track.albumTitle() : null,
        null,
        matchingAlbum ? track.coverImageUrl() : null,
        List.of(),
        track);
  }

  private void logMetadataFailure(Long musicItemId, BusinessException exception) {
    log.warn(
        "Archive music metadata unavailable for {}: {}", musicItemId, exception.getErrorCode());
  }

  private LocalDate toKoreaDate(LocalDateTime createdAt) {
    return koreaTime(createdAt).toLocalDate();
  }

  private OffsetDateTime koreaTime(LocalDateTime createdAt) {
    return createdAt
        .atZone(ZoneId.systemDefault())
        .withZoneSameInstant(KOREA_ZONE)
        .toOffsetDateTime();
  }

  private BigDecimal rating(Byte halfSteps) {
    return halfSteps == null ? null : BigDecimal.valueOf(halfSteps * 5L, 1);
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
