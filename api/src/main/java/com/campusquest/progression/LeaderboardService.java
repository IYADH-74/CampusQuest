package com.campusquest.progression;

import com.campusquest.common.Text;
import com.campusquest.common.web.PageResponse;
import com.campusquest.progression.ProgressionApi.LeaderboardEntry;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ranks active students by XP. Ties share a rank (SQL {@code rank()}), and only display names are exposed. */
@Service
public class LeaderboardService {

    private final JdbcClient jdbc;

    public LeaderboardService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaderboardEntry> page(UUID viewerId, UUID cohortId, int page, int size) {
        String filter = cohortId == null ? "" : " and u.cohort_id = :cohortId";
        long total = jdbc.sql("select count(*) from users u where u.role = 'STUDENT' and u.status = 'ACTIVE'" + filter)
                .params(params(cohortId))
                .query(Long.class)
                .single();
        var rows = jdbc.sql("""
                select ranked.* from (
                    select u.id, u.first_name, u.last_name, u.xp, u.level, c.code as cohort_code,
                           rank() over (order by u.xp desc) as rank
                    from users u left join cohorts c on c.id = u.cohort_id
                    where u.role = 'STUDENT' and u.status = 'ACTIVE'""" + filter + """
                ) ranked
                order by ranked.rank, ranked.id
                limit :limit offset :offset
                """)
                .params(params(cohortId))
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, n) -> new LeaderboardEntry(
                        rs.getLong("rank"),
                        Text.displayName(rs.getString("first_name"), rs.getString("last_name")),
                        rs.getString("cohort_code"),
                        rs.getInt("level"),
                        rs.getLong("xp"),
                        rs.getObject("id", UUID.class).equals(viewerId)))
                .list();
        return PageResponse.of(rows, page, size, total);
    }

    private static java.util.Map<String, Object> params(UUID cohortId) {
        return cohortId == null ? java.util.Map.of() : java.util.Map.of("cohortId", cohortId);
    }
}
