package com.rainbow.rlead.domain.repository;

import com.rainbow.rlead.domain.model.Lead;
import com.rainbow.rlead.domain.model.LeadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 리드 리포지토리.
 */
@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    /**
     * 상태별 리드 조회.
     */
    List<Lead> findByStatusOrderByCreatedAtDesc(LeadStatus status);

    /**
     * 모든 리드를 생성일 기준 내림차순으로 조회.
     */
    List<Lead> findAllByOrderByCreatedAtDesc();

    /**
     * 같은 사진(한 사진에 여러 명함)을 공유하는 다른 리드 수.
     */
    long countByCardImageKeyAndIdNot(String cardImageKey, Long id);

    /**
     * client_uuid로 리드 조회.
     */
    Optional<Lead> findByClientUuid(String clientUuid);

    /**
     * 이메일로 리드 조회.
     */
    @Query("SELECT l FROM Lead l WHERE l.email = :email AND l.email IS NOT NULL")
    List<Lead> findByEmail(@Param("email") String email);

    /**
     * 전화번호로 리드 조회.
     */
    @Query("SELECT l FROM Lead l WHERE l.phone = :phone AND l.phone IS NOT NULL")
    List<Lead> findByPhone(@Param("phone") String phone);

    /**
     * 회사명으로 리드 조회.
     */
    @Query("SELECT l FROM Lead l WHERE l.company = :company AND l.company IS NOT NULL")
    List<Lead> findByCompany(@Param("company") String company);
}
