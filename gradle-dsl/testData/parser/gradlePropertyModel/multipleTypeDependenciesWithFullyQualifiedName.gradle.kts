extra["prop1"] = "value1"
val prop1 = true
val prop2 by extra("${prop1} and ${project.extra["prop1"]}")
