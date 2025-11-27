package com.groupware.batch;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import com.groupware.common.config.DatabaseConfigurator;
import com.groupware.common.constant.CommonConstants;
import com.groupware.common.model.DepartmentType;
import com.groupware.common.registry.DepartmentRegistry;
import com.groupware.dao.NumberPaidDaysDao;
import com.groupware.dao.UserDao;
import com.groupware.dto.DepartmentTypeDto;
import com.groupware.dto.UserDto;
import com.groupware.employee.service.EmployeeService;
import com.groupware.userflow.service.UserFlowService;

/**
* PaidGrantBatch
* 有給付与処理バッチ
* 
* @return　
*/
@SpringBootApplication
@ComponentScan(basePackages = { "com.groupware" })
public class PaidGrantBatch {
	private final EmployeeService employeeService;
	private final NumberPaidDaysDao numberPaidDaysDao;
	private final UserDao userDao;
	private final UserFlowService userFlowService;

	public PaidGrantBatch(EmployeeService employeeService, NumberPaidDaysDao numberPaidDaysDao, UserDao userDao,
			UserFlowService userFlowService) {
		this.employeeService = employeeService;
		this.numberPaidDaysDao = numberPaidDaysDao;
		this.userDao = userDao;
		this.userFlowService = userFlowService;
	}

	public static void main(String[] args) {

		System.out.println("--- PaidGrantBatchバッチ処理を開始 ---");
		// DB接続設定
		DatabaseConfigurator.configureDatabaseProperties();

		// Springコンテナを起動し、PaidAcquisition インスタンスを取得
		try (ConfigurableApplicationContext context = SpringApplication.run(PaidGrantBatch.class, args)) {
			// コンテナから PaidAcquisition の Bean (DI済みインスタンス) を取得する
			PaidGrantBatch batch = context.getBean(PaidGrantBatch.class);

			// ロジック実行を DI 済みの非staticメソッドに委譲
			batch.executeBatchLogic();

		} catch (Exception e) {
			System.out.println("--- 有給付与処理中にエラーが発生しました（main）: " + e);
			e.printStackTrace();
		}

		System.out.println("--- PaidGrantBatchバッチ処理を終了 ---");
	}

	private void executeBatchLogic() {
		try {
			// 部署マスター読込処理
			List<DepartmentTypeDto> departmentList = userFlowService.findByDepartmentList();
			DepartmentRegistry.initialize(departmentList);

			// 社員アカウント一覧データ取得
			List<UserDto> userLists = employeeService.findByUsersList(CommonConstants.UNSELECTED_CODE);
			userLists.stream()
					// 管理者ではないユーザーのみをフィルタリング
					.filter(userRs -> {
						DepartmentType dept = DepartmentRegistry.fromCode(userRs.getDepartment());
						// 管理者権限者 (dept.getAdmin() が true) ではない場合に true を返し、ストリームに残す
						return !dept.getAdmin();
					})
					// フィルタリングされたユーザーに対して、付与処理と取得処理を実行
					.forEach(userRs -> {
						// 有給付与処理
						paidLeaveProcessing(userRs);
					});
		} catch (Exception e) {
			System.out.println("--- 有給付与処理中にエラーが発生しました（executeBatchLogic）: " + e);
			e.printStackTrace();
		}
	}

	/**
	* 有給付与処理を行う
	* 
	* @param　dto UserDto
	* @return
	*/
	private void paidLeaveProcessing(UserDto dto) {
		try {
			//NumberPaidDaysDao numberPaidDaysDao = new NumberPaidDaysDaoImpl();
			//UserDao userDao = new UserDaoImpl();

			// 有給付与処理
			LocalDate paidGrantDate = LocalDate.parse(dto.getPaidGrantDate(), CommonConstants.DATE_FORMAT);
			// 有給付与日を過ぎたら（当日含む）付与
			if (!LocalDate.now().isBefore(paidGrantDate)) {
				int beforePaidGrant = dto.getPaidLeaveGranted(); // 前回の有給付与日数
				float paidLeaveRemaining = dto.getPaidLeaveRemaining(); // 有給残日数
				// 前回の有給付与日数を超えた有給残日数は削除
				paidLeaveRemaining = Math.min(paidLeaveRemaining, beforePaidGrant);

				// 今回付与すべき有給日数取得
				long monthsPassed = ChronoUnit.MONTHS
						.between(LocalDate.parse(dto.getHireDate(), CommonConstants.DATE_FORMAT), LocalDate.now());
				int paidLeaveGranted = numberPaidDaysDao.findByPaidLeaveRranted(dto.getEmployeeType(),
						(int) monthsPassed);
				paidLeaveRemaining += paidLeaveGranted;

				// 有給付与日、有給付与日数、有給残日数の更新
				userDao.paidLeaveUpdate(dto.getId(),
						paidGrantDate.plusYears(1).format(CommonConstants.STRDATE_FORMAT), paidLeaveGranted,
						paidLeaveRemaining);
			}

		} catch (Exception e) {
			System.out.println("--- 有給付与処理中にエラーが発生しました（paidLeaveProcessing）: " + e);
			e.printStackTrace();
		}
	}
}
