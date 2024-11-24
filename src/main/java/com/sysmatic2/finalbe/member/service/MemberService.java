package com.sysmatic2.finalbe.member.service;

import com.sysmatic2.finalbe.exception.ConfirmPasswordMismatchException;
import com.sysmatic2.finalbe.exception.MemberAlreadyExistsException;
import com.sysmatic2.finalbe.exception.MemberNotFoundException;
import com.sysmatic2.finalbe.member.dto.DetailedProfileDTO;
import com.sysmatic2.finalbe.member.dto.SignupDTO;
import com.sysmatic2.finalbe.member.dto.SimpleProfileDTO;
import com.sysmatic2.finalbe.member.entity.MemberEntity;
import com.sysmatic2.finalbe.member.repository.MemberRepository;
import com.sysmatic2.finalbe.util.DtoEntityConversionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public void signup(SignupDTO signupDTO) {

        // nickname 중복 여부 & 비밀번호 동열 여부 확인
        duplicateNicknameCheck(signupDTO.getNickname());
        if (!signupDTO.getPassword().equals(signupDTO.getConfirmPassword())) {
            throw new ConfirmPasswordMismatchException("확인 비밀번호가 일치하지 않습니다.");
        }

        MemberEntity member = DtoEntityConversionUtils.convertToMemberEntity(signupDTO, passwordEncoder);

        //TODO) fileService 에서 프로필 사진 등록하는 메서드 호출 후 fileId 획득해서 치환하기
        String fileId = "1234";
        member.setFileId(fileId);

        memberRepository.save(member); // 가입 실패 시 예외 발생
    }

    // email 중복 여부 확인
    public void duplicateEmailCheck(String email) {
        // email로 디비에서 데이터 조회 -> 존재하면 중복 예외 발생 (탈퇴 시 개인정보 바로 삭제하므로 회원 상태 비교 불필요)
        if (!memberRepository.findByEmail(email).isEmpty()) {
            throw new MemberAlreadyExistsException("이미 사용 중인 이메일입니다.");
        }
    }

    // nickname 중복 여부 확인
    public void duplicateNicknameCheck(String nickname) {
        // email로 디비에서 데이터 조회 -> 존재하면 중복 예외 발생 (탈퇴 시 개인정보 바로 삭제하므로 회원 상태 비교 불필요)
        if (!memberRepository.findByNickname(nickname).isEmpty()) {
            throw new MemberAlreadyExistsException("이미 사용 중인 닉네임입니다.");
        }
    }

    //로그인 서비스
    public ResponseEntity<Map<String,Object>> login(String email, String password) {
        MemberEntity member = memberRepository.findByEmail(email)
                .orElse(null);
        Map<String,Object> response = new HashMap<>();

        if(member == null) {
            //이메일로 사용자를 찾지 못했을 경우(404)
            response.put("status","error");
            response.put("message","해당 계정이 존재하지 않습니다.");
            response.put("errorCode","MEMBER_NOT_FOUND");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        if(member.getIsLoginLocked()==('Y')){
            // 계정이 잠금 상태일 경우 (401)
            response.put("status", "error");
            response.put("message", "5회이상 로그인 실패로 잠금처리되었습니다. 이메일인증을 통해 비밀번호를 재설정하세요.");
            response.put("errorCode", "LOGIN_ATTEMPTS_EXCEEDED");
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }

        if (!passwordEncoder.matches(password, member.getPassword())) {            // 비밀번호가 일치하지 않는 경우 (401 로그인 실패)
            response.put("status", "error");
            response.put("message", "아이디 또는 비밀번호가 일치하지 않습니다.");
            response.put("errorCode", "INVALID_PASSWORD");
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }

        // 로그인 성공 (200)
        response.put("status", "success");
        response.put("message", "로그인에 성공했습니다.");
        Map<String, Object> data = new HashMap<>();
        data.put("member_id",member.getMemberId());
        data.put("email", member.getEmail());
        data.put("nickname", member.getNickname());
        data.put("role", member.getMemberGradeCode());
        //jwt 값을 전달해줘야지 정상적으로 로그인 했으면
        response.put("data", data);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public SimpleProfileDTO getSimpleProfile(String memberId) {
        Optional<SimpleProfileDTO> simpleProfileByMemberId = memberRepository.findSimpleProfileByMemberId(memberId);

        if(simpleProfileByMemberId.isEmpty()) {
            throw new MemberNotFoundException("존재하지 않는 회원입니다.");
        }

        return simpleProfileByMemberId.get();
    }

    public DetailedProfileDTO getDetailedProfile(String memberId) {
        Optional<DetailedProfileDTO> detailedProfileByMemberId = memberRepository.findDetailedProfileByMemberId(memberId);

        if (detailedProfileByMemberId.isEmpty()) {
            throw new MemberNotFoundException("존재하지 않는 회원입니다.");
        }

        return detailedProfileByMemberId.get();
    }

}
