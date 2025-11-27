package com.groupware.batch;

import java.time.YearMonth;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import com.groupware.common.config.DatabaseConfigurator;
import com.groupware.common.constant.CommonConstants;
import com.groupware.common.model.DepartmentType;
import com.groupware.common.model.VacationCategory;
import com.groupware.common.registry.DepartmentRegistry;
import com.groupware.common.registry.VacationCategoryRegistry;
import com.groupware.dao.AttendanceDao;
import com.groupware.dao.UserDao;
import com.groupware.dto.AttendanceDto;
import com.groupware.dto.DepartmentTypeDto;
import com.groupware.dto.UserDto;
import com.groupware.dto.VacationCategoryDto;
import com.groupware.employee.service.EmployeeService;
import com.groupware.userflow.service.UserFlowService;

/**
* PaidAcquisitionBatch
* 有給取得処理バッチ
* 
* @param　args 作業対象年月
* @return　
*/
@SpringBootApplication
@ComponentScan(basePackages = { "com.groupware" })
public class PaidAcquisitionBatch {
	private final EmployeeService employeeService;
	private final AttendanceDao attendanceDao;
	private final UserDao userDao;
	private final UserFlowService userFlowService;

	public PaidAcquisitionBatch(EmployeeService employeeService, AttendanceDao attendanceDao, UserDao userDao,
			UserFlowService userFlowService) {
		this.employeeService = employeeService;
		this.attendanceDao = attendanceDao;
		this.userDao = userDao;
		this.userFlowService = userFlowService;
	}

	public static void main(String[] args) {
		// コマンドライン引数が存在するかチェック
		if (args.length == 0) {
			System.err.println("エラー: コマンドライン引数の作業対象年月が指定されていません。");
			return;
		}

		System.out.println("--- PaidAcquisitionBatchバッチ処理を開始 ---");
		// DB接続設定
		DatabaseConfigurator.configureDatabaseProperties();

		// Springコンテナを起動し、PaidAcquisition インスタンスを取得
		try (ConfigurableApplicationContext context = SpringApplication.run(PaidAcquisitionBatch.class, args)) {
			// コンテナから PaidAcquisition の Bean (DI済みインスタンス) を取得する
			PaidAcquisitionBatch batch = context.getBean(PaidAcquisitionBatch.class);

			// ロジック実行を DI 済みの非staticメソッドに委譲
			batch.executeBatchLogic(args[0]);

		} catch (Exception e) {
			System.out.println("--- 有給取得処理中にエラーが発生しました（main）: " + e);
			e.printStackTrace();
		}

		System.out.println("--- PaidAcquisitionBatchバッチ処理を終了 ---");
	}

	private void executeBatchLogic(String yearMonthStr) {
		try {
			YearMonth ym = YearMonth.parse(yearMonthStr);

			// 部署マスター読込処理
			List<DepartmentTypeDto> departmentList = userFlowService.findByDepartmentList();
			DepartmentRegistry.initialize(departmentList);

			// 勤怠休暇区分マスター読込処理
			List<VacationCategoryDto> vacationCategoryList = userFlowService.findByVacationCategoryList();
			VacationCategoryRegistry.initialize(vacationCategoryList);

			// 社員アカウント一覧データ取得
			List<UserDto> userLists = employeeService.findByUsersList(CommonConstants.UNSELECTED_CODE);

			userLists.stream()
					// 管理者ではないユーザーのみをフィルタリング
					.filter(userRs -> {
						DepartmentType dept = DepartmentRegistry.fromCode(userRs.getDepartment());
						return !dept.getAdmin();
					})
					// フィルタリングされたユーザーに対して、付与処理と取得処理を実行
					.forEach(userRs -> {
						paidLeaveAcquisitionProcessing(userRs, ym);
					});
		} catch (Exception e) {
			System.out.println("--- 有給取得処理中にエラーが発生しました（executeBatchLogic）: " + e);
			e.printStackTrace();
		}
	}

	/**
	* 有給所得処理を行う
	* 
	* @param　dto UserDto
	* @param　ym 作業対象年月
	* @return
	*/
	private void paidLeaveAcquisitionProcessing(UserDto dto, YearMonth ym) { // static を削除
		try {
			List<AttendanceDto> attendanceLists = attendanceDao.findByUserAndMonthPaidDate(dto.getId(), ym);

			// 作業対象年月で取得した有給日を取得
			double paidVacationDate = attendanceLists.stream()
					.map(AttendanceDto::getVacationCategory)
					.map(VacationCategoryRegistry::fromCode)
					.filter(VacationCategory::getIsPaid)
					.mapToDouble(VacationCategory::getPaidDate)
					.sum();

			// 有給取得していたら有給残日数を更新
			float paidLeaveRemaining = dto.getPaidLeaveRemaining();
			if (paidVacationDate > 0) {
				paidLeaveRemaining -= paidVacationDate;
				paidLeaveRemaining = Math.max(0, paidLeaveRemaining);
				userDao.paidLeaveRemainingUpdate(dto.getId(), paidLeaveRemaining);
			}

		} catch (Exception e) {
			System.out.println("--- 有給取得処理中にエラーが発生しました（paidLeaveAcquisitionProcessing）: " + e);
			e.printStackTrace();
		}
	}
}
