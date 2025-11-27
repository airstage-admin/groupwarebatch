package com.groupware.batch;

import java.time.MonthDay;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import com.groupware.attendance.service.AttendanceService;
import com.groupware.common.config.DatabaseConfigurator;
import com.groupware.common.constant.CommonConstants;
import com.groupware.common.model.DepartmentType;
import com.groupware.common.registry.DepartmentRegistry;
import com.groupware.common.registry.PlaceCategoryRegistry;
import com.groupware.dto.DepartmentTypeDto;
import com.groupware.dto.PlaceCategoryDto;
import com.groupware.dto.PublicHolidayDto;
import com.groupware.dto.UserDto;
import com.groupware.employee.service.EmployeeService;
import com.groupware.userflow.service.UserFlowService;

/**
* AttendanceCreateBatch
* 対象月の勤怠管理簿作成処理バッチ
* 
* @return　
*/
@SpringBootApplication
@ComponentScan(basePackages = { "com.groupware" })
public class AttendanceCreateBatch {
	private final EmployeeService employeeService;
	private final UserFlowService userFlowService;
	private final AttendanceService attendanceService;

	public AttendanceCreateBatch(EmployeeService employeeService, UserFlowService userFlowService,
			AttendanceService attendanceService) {
		this.employeeService = employeeService;
		this.userFlowService = userFlowService;
		this.attendanceService = attendanceService;
	}

	public static void main(String[] args) {

		System.out.println("--- AttendanceCreateBatchバッチ処理を開始 ---");
		// DB接続設定
		DatabaseConfigurator.configureDatabaseProperties();

		SpringApplication application = new SpringApplication(AttendanceCreateBatch.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		try (ConfigurableApplicationContext context = application.run(args)) {
			// コンテナから PaidAcquisition の Bean (DI済みインスタンス) を取得する
			AttendanceCreateBatch batch = context.getBean(AttendanceCreateBatch.class);

			// ロジック実行を DI 済みの非staticメソッドに委譲
			batch.executeBatchLogic();

		} catch (Exception e) {
			System.out.println("--- 対象月の勤怠管理簿作成処理中にエラーが発生しました（main）: " + e);
			e.printStackTrace();
		}

		System.out.println("--- AttendanceCreateBatchバッチ処理を終了 ---");
	}

	private void executeBatchLogic() {
		try {
			// 部署マスター読込処理
			List<DepartmentTypeDto> departmentList = userFlowService.findByDepartmentList();
			DepartmentRegistry.initialize(departmentList);

			// 勤務先区分マスター読込処理
			List<PlaceCategoryDto> placeCategoryList = userFlowService.findByPlaceCategoryList();
			PlaceCategoryRegistry.initialize(placeCategoryList);

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
						createIntialAttendance(userRs.getId());
					});
		} catch (Exception e) {
			System.out.println("--- 対象月の勤怠管理簿作成処理中にエラーが発生しました（executeBatchLogic）: " + e);
			e.printStackTrace();
		}
	}

	/**
	* 勤怠初期データの作成処理を行う
	* 
	* @param　userID ユーザーID
	* @return　true：成功、false：エラー
	*/
	private void createIntialAttendance(long userId) {
		try {
			// 公休日一覧データ取得
			List<PublicHolidayDto> list = attendanceService.findByHolidayList();

			// 公休日を設定する
			Set<MonthDay> combinedHolidays = new HashSet<>(CommonConstants.TARGET_DAYS);
			list.stream()
					.map(dto -> MonthDay.of(dto.getMonth(), dto.getDay()))
					.forEach(combinedHolidays::add);
			CommonConstants.TARGET_DAYS = combinedHolidays;

			// 今月、前月取得
			YearMonth currentMonth = YearMonth.now();
			YearMonth previousMonth = currentMonth.minusMonths(1);

			// 無ければ前月の勤怠初期データの作成処理を行う
			if (!attendanceService.existsByInitialAttendanceDate(userId, previousMonth)) {
				String[] arrayPrevious = previousMonth.toString().split(CommonConstants.ST_HYPHEN);
				insertIntialAttendance(arrayPrevious[0], arrayPrevious[1], userId);
			}

			// 無ければ今月の勤怠初期データの作成処理を行う
			if (!attendanceService.existsByInitialAttendanceDate(userId, currentMonth)) {
				String[] arrayCurrent = currentMonth.toString().split(CommonConstants.ST_HYPHEN);
				insertIntialAttendance(arrayCurrent[0], arrayCurrent[1], userId);
			}

		} catch (Exception e) {
			System.out.println("--- 対象月の勤怠管理簿作成処理中にエラーが発生しました（createIntialAttendance）: " + e);
			e.printStackTrace();
		}
	}

	/**
	* 対象年月の勤怠データの初期データをInsertする
	* 
	* @param　yearMonth 対象年
	* @param　month 対象月
	* @param　userID ユーザーID
	* @return
	*/
	private void insertIntialAttendance(String year, String month, long userId) throws Exception {
		attendanceService.makeMonthAttendance(userId, Integer.parseInt(year), Integer.parseInt(month));
	}
}
