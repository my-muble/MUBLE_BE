package com.typeforsheet.backend.domain.home.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typeforsheet.backend.domain.music.dto.AlbumDetailResponse;
import com.typeforsheet.backend.domain.music.dto.ResolveMusicRequest;
import com.typeforsheet.backend.domain.music.dto.ResolveMusicResponse;
import com.typeforsheet.backend.domain.music.entity.ExternalIdType;
import com.typeforsheet.backend.domain.music.entity.MusicExternalId;
import com.typeforsheet.backend.domain.music.entity.MusicItem;
import com.typeforsheet.backend.domain.music.entity.MusicProvider;
import com.typeforsheet.backend.domain.music.entity.MusicType;
import com.typeforsheet.backend.domain.music.repository.MusicExternalIdRepository;
import com.typeforsheet.backend.domain.music.repository.MusicItemRepository;
import com.typeforsheet.backend.domain.music.service.AlbumDetailService;
import com.typeforsheet.backend.domain.music.service.MusicResolveService;
import com.typeforsheet.backend.domain.record.entity.MusicRecord;
import com.typeforsheet.backend.domain.record.repository.MusicRecordRepository;
import com.typeforsheet.backend.domain.search.provider.MusicBrainzCatalogProvider;
import com.typeforsheet.backend.domain.user.entity.User;
import com.typeforsheet.backend.domain.user.repository.UserRepository;
import com.typeforsheet.backend.global.exception.BusinessException;
import com.typeforsheet.backend.global.exception.ErrorCode;
import com.typeforsheet.backend.global.security.JwtTokenProvider;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HomeRankingControllerTest {

  private static final String URL = "/api/home/rankings";

  private static final LocalDateTime NOW = LocalDateTime.of(2099, 1, 1, 12, 0, 0, 123456000);

  private static final String RECORDING_ID = "11111111-1111-1111-1111-111111111111";

  private static final String SECOND_RECORDING_ID = "22222222-2222-2222-2222-222222222222";

  private static final String RELEASE_GROUP_ID = "33333333-3333-3333-3333-333333333333";

  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired MusicItemRepository items;
  @Autowired MusicExternalIdRepository externalIds;
  @Autowired MusicRecordRepository records;
  @Autowired JwtTokenProvider tokens;
  @Autowired JdbcTemplate jdbc;

  @MockitoBean Clock clock;
  @MockitoBean AlbumDetailService albums;
  @MockitoBean MusicBrainzCatalogProvider catalog;
  @MockitoBean MusicResolveService musicResolver;

  @BeforeEach
  void fixRequestTime() {
    ZoneId zone = ZoneId.systemDefault();
    when(clock.getZone()).thenReturn(zone);
    when(clock.instant()).thenReturn(NOW.atZone(zone).toInstant());
  }

  @Test
  void rejectsUnauthenticatedRequests() throws Exception {
    mvc.perform(get(URL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  @Test
  void returnsEmptyArrayWhenNoPublicRecordsExist() throws Exception {
    User viewer = user("ranking-empty-viewer");

    mvc.perform(authorized(viewer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.length()").value(0));

    verifyNoInteractions(catalog, albums, musicResolver);
  }

  @Test
  void sumsAlbumAndTrackRecordsIncludingOwnRepeatedRecords() throws Exception {
    User author = user("ranking-author");
    User another = user("ranking-another");

    MusicItem album = music(MusicType.ALBUM);
    MusicItem track = music(MusicType.TRACK);

    attachRecording(track, RECORDING_ID);
    representative(RECORDING_ID, RELEASE_GROUP_ID);
    resolveToAlbum(RELEASE_GROUP_ID, album);
    albumDetails(album);

    // 별점과 본문이 모두 있는 기록: 2점
    save(author, album, (byte) 9, "both", false, NOW.minusMinutes(20));

    // 본문만 있는 기록: 1점
    save(author, album, null, "text only", false, NOW.minusMinutes(19));

    // 0.5점 별점만 있는 기록: 2점
    save(author, album, (byte) 1, null, false, NOW.minusMinutes(18));

    // 같은 작성자가 같은 곡에 두 번 작성한 기록도 각각 집계
    save(author, track, (byte) 10, null, false, NOW.minusMinutes(17));
    save(author, track, null, "second track record", false, NOW.minusMinutes(16));

    // 다른 사용자의 공개 기록도 포함
    save(another, track, null, "another author", false, NOW.minusMinutes(15));

    // 제외할 기록
    save(author, album, (byte) 10, "private", true, NOW.minusMinutes(14));
    save(author, album, (byte) 10, "old", false, NOW.minusHours(2));
    save(author, album, (byte) 10, "future", false, NOW.plusMinutes(1));

    // 전체 6건 + 별점 있는 기록 3건 = 9점
    mvc.perform(authorized(author))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].albumId").value(album.getId()))
        .andExpect(jsonPath("$.data[0].title").value("album-" + album.getId()))
        .andExpect(jsonPath("$.data[0].artistName").value("artist"))
        .andExpect(jsonPath("$.data[0].coverImageUrl").value("cover"))
        .andExpect(jsonPath("$.data[0].rankingScore").value(9));

    verify(catalog, times(1)).getTrackDetail(RECORDING_ID);
    verify(musicResolver, times(1))
        .resolve(new ResolveMusicRequest("MUSICBRAINZ", "RELEASE_GROUP", RELEASE_GROUP_ID));
    verify(albums, times(1)).getAlbum(album.getId());
  }

  @Test
  void includesBothOneHourBoundariesWithMicrosecondPrecision() throws Exception {
    User viewer = user("ranking-boundary");
    MusicItem album = music(MusicType.ALBUM);
    albumDetails(album);

    LocalDateTime start = NOW.minusHours(1);

    save(viewer, album, (byte) 1, null, false, start);
    save(viewer, album, null, "upper boundary", false, NOW);

    save(viewer, album, (byte) 10, null, false, start.minusNanos(1000));
    save(viewer, album, (byte) 10, null, false, NOW.plusNanos(1000));

    // 구간 [start, NOW]의 2건만 포함. 별점 1건.
    mvc.perform(authorized(viewer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].rankingScore").value(3));
  }

  @Test
  void sortsTiesByLatestRecordThenAlbumId() throws Exception {
    User viewer = user("ranking-ties");

    MusicItem oldest = music(MusicType.ALBUM);
    MusicItem firstTie = music(MusicType.ALBUM);
    MusicItem secondTie = music(MusicType.ALBUM);

    albumDetails(oldest);
    albumDetails(firstTie);
    albumDetails(secondTie);

    save(viewer, oldest, (byte) 8, null, false, NOW.minusMinutes(30));
    save(viewer, firstTie, (byte) 8, null, false, NOW.minusMinutes(10));
    save(viewer, secondTie, (byte) 8, null, false, NOW.minusMinutes(10));

    // 모두 2점. 최신 기록이 같은 두 앨범은 ID가 작은 순.
    mvc.perform(authorized(viewer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(3))
        .andExpect(jsonPath("$.data[0].albumId").value(firstTie.getId()))
        .andExpect(jsonPath("$.data[1].albumId").value(secondTie.getId()))
        .andExpect(jsonPath("$.data[2].albumId").value(oldest.getId()));
  }

  @Test
  void limitsResultsOnlyAfterAlbumAggregationAndSorting() throws Exception {
    User viewer = user("ranking-limit");

    List<MusicItem> candidateAlbums = new ArrayList<>();

    for (int i = 0; i < 12; i++) {
      MusicItem album = music(MusicType.ALBUM);
      candidateAlbums.add(album);
      albumDetails(album);

      save(viewer, album, null, "ranking candidate", false, NOW.minusMinutes(i));
    }

    mvc.perform(authorized(viewer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(10))
        .andExpect(jsonPath("$.data[0].albumId").value(candidateAlbums.get(0).getId()))
        .andExpect(jsonPath("$.data[9].albumId").value(candidateAlbums.get(9).getId()));

    verify(albums, times(10)).getAlbum(anyLong());
    verify(albums, never()).getAlbum(candidateAlbums.get(10).getId());
    verify(albums, never()).getAlbum(candidateAlbums.get(11).getId());
  }

  @Test
  void excludesTracksWithoutRepresentativeAlbum() throws Exception {
    User viewer = user("ranking-no-representative");

    MusicItem album = music(MusicType.ALBUM);
    MusicItem track = music(MusicType.TRACK);

    albumDetails(album);
    attachRecording(track, RECORDING_ID);
    representative(RECORDING_ID, null);

    save(viewer, album, null, "album record", false, NOW.minusMinutes(5));
    save(viewer, track, (byte) 10, null, false, NOW.minusMinutes(4));

    mvc.perform(authorized(viewer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].albumId").value(album.getId()))
        .andExpect(jsonPath("$.data[0].rankingScore").value(1));

    verifyNoInteractions(musicResolver);
  }

  @Test
  void resolvesSharedRepresentativeAlbumOnlyOnce() throws Exception {
    User viewer = user("ranking-shared-album");

    MusicItem album = music(MusicType.ALBUM);
    MusicItem trackA = music(MusicType.TRACK);
    MusicItem trackB = music(MusicType.TRACK);

    attachRecording(trackA, RECORDING_ID);
    attachRecording(trackB, SECOND_RECORDING_ID);

    representative(RECORDING_ID, RELEASE_GROUP_ID);
    representative(SECOND_RECORDING_ID, RELEASE_GROUP_ID);

    resolveToAlbum(RELEASE_GROUP_ID, album);
    albumDetails(album);

    // 앨범 직접 기록이 없어도 곡 기록으로 랭킹에 올라온다.
    save(viewer, trackA, (byte) 8, null, false, NOW.minusMinutes(3));
    save(viewer, trackB, null, "track B", false, NOW.minusMinutes(2));

    mvc.perform(authorized(viewer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].albumId").value(album.getId()))
        .andExpect(jsonPath("$.data[0].rankingScore").value(3));

    verify(catalog, times(1)).getTrackDetail(RECORDING_ID);
    verify(catalog, times(1)).getTrackDetail(SECOND_RECORDING_ID);
    verify(musicResolver, times(1))
        .resolve(new ResolveMusicRequest("MUSICBRAINZ", "RELEASE_GROUP", RELEASE_GROUP_ID));
  }

  @Test
  void propagatesMusicBrainzFailuresInsteadOfReturningWrongRankings() throws Exception {
    User viewer = user("ranking-upstream-failure");
    MusicItem track = music(MusicType.TRACK);

    attachRecording(track, RECORDING_ID);
    save(viewer, track, (byte) 10, null, false, NOW.minusMinutes(2));

    when(catalog.getTrackDetail(RECORDING_ID))
        .thenThrow(new BusinessException(ErrorCode.MUSIC_CATALOG_UNAVAILABLE));

    mvc.perform(authorized(viewer))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("MUSIC_CATALOG_UNAVAILABLE"));

    verifyNoInteractions(musicResolver, albums);
  }

  @Test
  void propagatesMissingExternalMusicSeparately() throws Exception {
    User viewer = user("ranking-external-missing");
    MusicItem track = music(MusicType.TRACK);

    attachRecording(track, RECORDING_ID);
    save(viewer, track, null, "record", false, NOW.minusMinutes(2));

    when(catalog.getTrackDetail(RECORDING_ID))
        .thenThrow(new BusinessException(ErrorCode.MUSIC_EXTERNAL_ID_NOT_FOUND));

    mvc.perform(authorized(viewer))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MUSIC_EXTERNAL_ID_NOT_FOUND"));

    verifyNoInteractions(musicResolver, albums);
  }

  @Test
  void rejectsTracksWithMissingRecordingMappings() throws Exception {
    User viewer = user("ranking-mapping-missing");
    MusicItem track = music(MusicType.TRACK);

    save(viewer, track, null, "record", false, NOW.minusMinutes(2));

    mvc.perform(authorized(viewer))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("MUSIC_MAPPING_MISSING"));

    verifyNoInteractions(catalog, musicResolver, albums);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorized(
      User viewer) {
    return get(URL).header("Authorization", "Bearer " + tokens.createAccessToken(viewer.getId()));
  }

  private User user(String nickname) {
    return users.saveAndFlush(new User(nickname));
  }

  private MusicItem music(MusicType type) {
    return items.saveAndFlush(new MusicItem(type));
  }

  private void attachRecording(MusicItem track, String recordingId) {
    externalIds.saveAndFlush(
        new MusicExternalId(
            track, MusicProvider.MUSICBRAINZ, ExternalIdType.RECORDING, recordingId));
  }

  private void representative(String recordingId, String releaseGroupId) {
    when(catalog.getTrackDetail(recordingId))
        .thenReturn(
            new MusicBrainzCatalogProvider.TrackDetail(
                recordingId, "track", "artist", null, null, "album", releaseGroupId, "2020-01-01"));
  }

  private void resolveToAlbum(String releaseGroupId, MusicItem album) {
    when(musicResolver.resolve(
            new ResolveMusicRequest("MUSICBRAINZ", "RELEASE_GROUP", releaseGroupId)))
        .thenReturn(new ResolveMusicResponse(album.getId(), MusicType.ALBUM));
  }

  private void albumDetails(MusicItem album) {
    when(albums.getAlbum(album.getId()))
        .thenReturn(
            new AlbumDetailResponse(
                album.getId(),
                null,
                "album-" + album.getId(),
                "artist",
                null,
                null,
                null,
                "cover",
                List.of()));
  }

  private void save(
      User author,
      MusicItem item,
      Byte ratingHalfSteps,
      String content,
      boolean isPrivate,
      LocalDateTime createdAt) {

    MusicRecord record =
        records.saveAndFlush(new MusicRecord(author, item, ratingHalfSteps, content, isPrivate));

    jdbc.update(
        "UPDATE music_records SET created_at = ? WHERE id = ?",
        Timestamp.valueOf(createdAt),
        record.getId());
  }
}
