package gg.duo.post.domain.requirement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PostGameRequirementRepository extends JpaRepository<PostGameRequirement, Long> {

    List<PostGameRequirement> findByPostId(Long postId);

    List<PostGameRequirement> findByPostIdIn(Collection<Long> postIds);

    void deleteByPostId(Long postId);
}
