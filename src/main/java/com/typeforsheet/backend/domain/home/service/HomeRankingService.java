package com.typeforsheet.backend.domain.home.service;

import com.typeforsheet.backend.domain.home.dto.response.HomeRankingResponse;
import com.typeforsheet.backend.domain.home.repository.HomeRankingCountRow;
import com.typeforsheet.backend.domain.home.repository.HomeRankingRepository;
import com.typeforsheet.backend.domain.music.dto.ResolveMusicRequest;
import com.typeforsheet.backend.domain.music.entity.ExternalIdType;
import com.typeforsheet.backend.domain.music.entity.MusicExternalId;
import com.typeforsheet.backend.domain.music.entity.MusicProvider;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.repository.MusicExternalIdRepository;
import com.typeforsheet.backend.domain.music.service.AlbumDetailService;
import com.typeforsheet.backend.domain.music.service.MusicResolveService;
import com.typeforsheet.backend.domain.search.provider.MusicBrainzCatalogProvider;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HomeRankingService {

  private static final int MAX_RANKINGS = 10;

  private final HomeRankingRepository rankings;
  private final MusicExternalIdRepository externalIds;
  private final MusicBrainzCatalogProvider catalog;
  private final MusicResolveService musicResolver;
  private final AlbumDetailService albums;
  private final Clock clock;

  public List<HomeRankingResponse> getRankings() {

    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    LocalDateTime from = now.minusHours(1);

    List<HomeRankingCountRow> rows = rankings.aggregatePublicRecords(from, now);

    if (rows.isEmpty()) {
      return List.of();
    }

    Map<Long, AlbumTotals> totalsByAlbum = new HashMap<>();

    // 동일 대표 releaseGroupId를 여러 곡이 공유해도 한 번만 연결한다.
    Map<String, Long> resolvedAlbumIds = new HashMap<>();

    for (HomeRankingCountRow row : rows) {

      Long albumId;

      if (row.getMusicType() == MusicType.ALBUM) {
        albumId = row.getMusicId();

      } else if (row.getMusicType() == MusicType.TRACK) {
        albumId = resolveAlbumForTrack(row.getMusicId(), resolvedAlbumIds);

      } else {
        throw new IllegalStateException("지원하지 않는 음악 유형입니다: " + row.getMusicType());
      }

      // 대표 앨범 후보가 실제로 없는 곡만 집계에서 제외한다.
      if (albumId == null) {
        continue;
      }

      totalsByAlbum.computeIfAbsent(albumId, ignored -> new AlbumTotals()).add(row);
    }

    List<AlbumRank> ordered =
        totalsByAlbum.entrySet().stream()
            .map(
                entry ->
                    new AlbumRank(
                        entry.getKey(), entry.getValue().score(), entry.getValue().latestCreatedAt))
            .sorted(
                Comparator.comparingLong(AlbumRank::score)
                    .reversed()
                    .thenComparing(AlbumRank::latestCreatedAt, Comparator.reverseOrder())
                    .thenComparing(AlbumRank::albumId))
            .limit(MAX_RANKINGS)
            .toList();

    // 순위가 확정된 앨범에 대해서만 카드 메타데이터를 조회한다.
    return ordered.stream()
        .map(
            rank -> {
              var album = albums.getAlbum(rank.albumId());

              return new HomeRankingResponse(
                  rank.albumId(),
                  album.title(),
                  album.artistName(),
                  album.coverImageUrl(),
                  rank.score());
            })
        .toList();
  }

  private Long resolveAlbumForTrack(Long trackId, Map<String, Long> resolvedAlbumIds) {

    String recordingId =
        externalIds
            .findFirstByMusicItem_IdAndProviderAndExternalIdTypeOrderByIdAsc(
                trackId, MusicProvider.MUSICBRAINZ, ExternalIdType.RECORDING)
            .map(MusicExternalId::getExternalId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MUSIC_MAPPING_MISSING));

    // 기존 대표 앨범 선택 로직을 재사용한다.
    // TrackDetailService와 달리 커버 조회는 하지 않는다.
    var track = catalog.getTrackDetail(recordingId);

    String releaseGroupId = track.releaseGroupId();

    if (releaseGroupId == null) {
      return null;
    }

    // 기존 resolver는 매핑이 있으면 조회하고,
    // 없으면 canonical ID를 검증한 뒤 내부 앨범 ID를 등록한다.
    return resolvedAlbumIds.computeIfAbsent(
        releaseGroupId,
        id ->
            musicResolver
                .resolve(new ResolveMusicRequest("MUSICBRAINZ", "RELEASE_GROUP", id))
                .musicId());
  }

  private record AlbumRank(Long albumId, long score, LocalDateTime latestCreatedAt) {}

  private static final class AlbumTotals {

    private long recordCount;
    private long ratedRecordCount;
    private LocalDateTime latestCreatedAt;

    private void add(HomeRankingCountRow row) {

      recordCount = Math.addExact(recordCount, row.getRecordCount());

      ratedRecordCount = Math.addExact(ratedRecordCount, row.getRatedRecordCount());

      LocalDateTime candidate = row.getLatestCreatedAt();

      if (latestCreatedAt == null || candidate.isAfter(latestCreatedAt)) {
        latestCreatedAt = candidate;
      }
    }

    private long score() {
      return Math.addExact(recordCount, ratedRecordCount);
    }
  }
}
