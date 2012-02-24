# run it with 'R CMD BATCH graph-it.R'

library(ggplot2)

data <- read.table("usedmem-2012-02-24.txt")
y <- data$usedMem
x <- 1:20
coef <- lm(y~x)
a <- coef$coefficients[1]
b <- coef$coefficients[2]

p <- qplot(x, y, main="Memory usage when repeatedly typing in Typers.scala", xlab="Run", ylab="Memory (MB)")
p + geom_line() + geom_abline(intercept=a, slope=b)
