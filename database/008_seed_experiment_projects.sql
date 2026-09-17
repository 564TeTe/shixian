-- ============================================================
-- 批量生成实验项目数据：每个教学任务 3 个实验项目
-- 必做×2（验证型 + 综合型）+ 选做×1（设计研究型）
-- 项目名称按课程关键词智能匹配
-- ============================================================

USE t132;
SET NAMES utf8mb4;

-- 安全：如果已存在数据先不重复插入（按 project_code 前缀判断）
INSERT INTO experiment_project
(task_id, project_code, school_code, name, category_code, type_code,
 discipline_code, requirement_code, participant_type_code,
 group_size, hours, participant_count, sort_order, created_by_account_id)
SELECT
    t.id AS task_id,
    CONCAT('P-', t.id, '-', n.seq) AS project_code,
    '11059' AS school_code,
    CASE
        -- 数据结构与算法
        WHEN c.course_name LIKE '%数据结构%' THEN
            CASE n.seq
                WHEN 1 THEN '线性表与链表基本操作'
                WHEN 2 THEN '栈、队列与二叉树遍历实现'
                ELSE '排序算法综合比较与设计'
            END
        -- 操作系统
        WHEN c.course_name LIKE '%操作系统%' THEN
            CASE n.seq
                WHEN 1 THEN '进程创建与调度模拟'
                WHEN 2 THEN '内存管理与页面置换实验'
                ELSE '文件系统设计与实现'
            END
        -- 计算机网络
        WHEN c.course_name LIKE '%网络%' OR c.course_name LIKE '%路由%' OR c.course_name LIKE '%TCP%' OR c.course_name LIKE '%协议%' THEN
            CASE n.seq
                WHEN 1 THEN 'TCP/IP 协议抓包分析'
                WHEN 2 THEN 'HTTP 请求与响应报文解析'
                ELSE '路由协议配置与组网实验'
            END
        -- 数据库
        WHEN c.course_name LIKE '%数据库%' THEN
            CASE n.seq
                WHEN 1 THEN 'SQL 基础查询与多表连接'
                WHEN 2 THEN '索引设计与查询性能优化'
                ELSE '数据库完整性与事务设计'
            END
        -- 编程语言类
        WHEN c.course_name LIKE '%C语言%' OR c.course_name LIKE '%C++%' OR c.course_name LIKE '%C/C++%' THEN
            CASE n.seq
                WHEN 1 THEN '开发环境搭建与基础语法练习'
                WHEN 2 THEN '指针与内存操作综合实验'
                ELSE '小型项目模块化设计'
            END
        WHEN c.course_name LIKE '%Java%' OR c.course_name LIKE '%JAVA%' THEN
            CASE n.seq
                WHEN 1 THEN 'Java 面向对象编程基础'
                WHEN 2 THEN '集合框架与异常处理实验'
                ELSE 'JDBC 与小型应用设计'
            END
        WHEN c.course_name LIKE '%Python%' OR c.course_name LIKE '%python%' THEN
            CASE n.seq
                WHEN 1 THEN 'Python 语法与数据类型练习'
                WHEN 2 THEN '文件操作与第三方库使用'
                ELSE '数据处理脚本综合设计'
            END
        -- Web 前后端
        WHEN c.course_name LIKE '%Web%' OR c.course_name LIKE '%web%' OR c.course_name LIKE '%前端%' OR c.course_name LIKE '%JavaScript%' THEN
            CASE n.seq
                WHEN 1 THEN 'HTML/CSS 页面布局基础'
                WHEN 2 THEN 'JavaScript 交互与 DOM 操作'
                ELSE '前后端接口联调综合设计'
            END
        -- AI / 机器学习 / 深度学习 / NLP / 计算机视觉 / 模式识别
        WHEN c.course_name LIKE '%机器学习%' OR c.course_name LIKE '%深度学习%' OR c.course_name LIKE '%人工智能%'
             OR c.course_name LIKE '%自然语言%' OR c.course_name LIKE '%计算机视觉%' OR c.course_name LIKE '%模式识别%'
             OR c.course_name LIKE '%智能计算%' THEN
            CASE n.seq
                WHEN 1 THEN '数据集加载与预处理实验'
                WHEN 2 THEN '经典模型训练与参数调优'
                ELSE '模型评估与对比分析设计'
            END
        -- 大数据 / 数据挖掘 / 数据分析
        WHEN c.course_name LIKE '%大数据%' OR c.course_name LIKE '%数据挖掘%' OR c.course_name LIKE '%数据分析%'
             OR c.course_name LIKE '%数据采集%' OR c.course_name LIKE '%可视化%' THEN
            CASE n.seq
                WHEN 1 THEN '数据清洗与预处理实验'
                WHEN 2 THEN '统计分析与可视化实验'
                ELSE '数据挖掘模型综合应用'
            END
        -- 编译原理
        WHEN c.course_name LIKE '%编译%' THEN
            CASE n.seq
                WHEN 1 THEN '词法分析器设计与实现'
                WHEN 2 THEN '语法分析递归下降实验'
                ELSE '中间代码生成与优化'
            END
        -- 组成原理 / 微机原理 / 数字逻辑 / 电路
        WHEN c.course_name LIKE '%组成%' OR c.course_name LIKE '%微机%' OR c.course_name LIKE '%数字逻辑%'
             OR c.course_name LIKE '%电路%' OR c.course_name LIKE '%单片机%' THEN
            CASE n.seq
                WHEN 1 THEN '门电路与组合逻辑实验'
                WHEN 2 THEN '时序逻辑与寄存器实验'
                ELSE '简单处理器设计与验证'
            END
        -- 软件工程 / 软件测试 / 软件构造 / 软件分析 / 项目管理
        WHEN c.course_name LIKE '%软件工程%' OR c.course_name LIKE '%软件测试%' OR c.course_name LIKE '%软件构造%'
             OR c.course_name LIKE '%软件分析%' OR c.course_name LIKE '%软件项目管理%' OR c.course_name LIKE '%软件分析与设计%' THEN
            CASE n.seq
                WHEN 1 THEN '需求分析与用例建模实验'
                WHEN 2 THEN '单元测试与测试用例设计'
                ELSE '软件架构设计与评审'
            END
        -- 数学类
        WHEN c.course_name LIKE '%数学%' OR c.course_name LIKE '%离散%' OR c.course_name LIKE '%最优化%' OR c.course_name LIKE '%统计%' THEN
            CASE n.seq
                WHEN 1 THEN '基础算法推导与验证'
                WHEN 2 THEN '数学建模与求解实验'
                ELSE '综合案例分析与讨论'
            END
        -- 移动开发
        WHEN c.course_name LIKE '%Android%' OR c.course_name LIKE '%移动%' THEN
            CASE n.seq
                WHEN 1 THEN 'Android 界面与组件基础'
                WHEN 2 THEN 'Activity 与 Intent 跳转实验'
                ELSE '数据存储与网络访问设计'
            END
        -- 安全类
        WHEN c.course_name LIKE '%安全%' OR c.course_name LIKE '%攻防%' THEN
            CASE n.seq
                WHEN 1 THEN '常见 Web 漏洞复现与防御'
                WHEN 2 THEN '加密算法与证书实验'
                ELSE '安全攻防综合演练'
            END
        -- 嵌入式 / 控制 / 导航
        WHEN c.course_name LIKE '%嵌入式%' OR c.course_name LIKE '%控制%' OR c.course_name LIKE '%导航%' THEN
            CASE n.seq
                WHEN 1 THEN '开发板环境搭建与 GPIO 实验'
                WHEN 2 THEN '传感器数据采集与处理'
                ELSE '综合控制系统设计'
            END
        -- 其他课程（兜底）
        ELSE
            CASE n.seq
                WHEN 1 THEN '课程基础验证实验'
                WHEN 2 THEN '课程综合应用实验'
                ELSE '课程拓展研究实验'
            END
        END AS name,
    -- category_code: 1基础 2专业基础 3专业 4其他；基础课=1，专业课=3
    CASE
        WHEN c.course_name LIKE '%C语言%' OR c.course_name LIKE '%C++%' OR c.course_name LIKE '%Python%' OR c.course_name LIKE '%Java%'
             OR c.course_name LIKE '%JAVA%' OR c.course_name LIKE '%离散%' OR c.course_name LIKE '%计算机基础%'
             OR c.course_name LIKE '%数字逻辑%' OR c.course_name LIKE '%数学%' THEN '1'
        ELSE '3'
    END AS category_code,
    -- type_code: 1演示 2验证 3综合 4设计研究 5其他
    CASE n.seq WHEN 1 THEN '2' WHEN 2 THEN '3' ELSE '4' END AS type_code,
    '0809' AS discipline_code,
    -- requirement_code: 1必做 2选做 3其他；前两个必做，第三个选做
    CASE WHEN n.seq = 3 THEN '2' ELSE '1' END AS requirement_code,
    '3' AS participant_type_code,  -- 本科生
    CASE WHEN c.course_name LIKE '%设计%' OR c.course_name LIKE '%研究%' THEN 2 ELSE 1 END AS group_size,
    CASE n.seq WHEN 1 THEN 2.00 WHEN 2 THEN 4.00 ELSE 6.00 END AS hours,
    t.enrollment_count AS participant_count,  -- 用选课人数作为参考参与人数
    n.seq AS sort_order,
    1 AS created_by_account_id
FROM teaching_task t
INNER JOIN course c ON c.id = t.course_id
CROSS JOIN (SELECT 1 AS seq UNION SELECT 2 UNION SELECT 3) n
WHERE NOT EXISTS (
    SELECT 1 FROM experiment_project p WHERE p.task_id = t.id
)
ORDER BY t.id, n.seq;

-- 核对结果
SELECT COUNT(*) AS inserted_projects FROM experiment_project;
SELECT task_id, project_code, name, type_code, requirement_code, hours
FROM experiment_project
ORDER BY id DESC LIMIT 15;
