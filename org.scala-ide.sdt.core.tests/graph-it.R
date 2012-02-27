# run it with 'Rscript graph-it.R <filename>'

library(ggplot2)

args <- commandArgs(TRUE)
if (length(args) < 1) {
	stop("Please pass the filename with the memory data.")
}


data <- read.table(args[1])
y <- data$usedMem
x <- 1:20
coef <- lm(y~x)
a <- coef$coefficients[1]
b <- coef$coefficients[2]

p <- qplot(x, y, main="Memory usage when repeatedly typing in Typers.scala", xlab="Run", ylab="Memory (MB)")
p + geom_line() + geom_abline(intercept=a, slope=b)
